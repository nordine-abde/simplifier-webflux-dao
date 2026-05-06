const state = {
  page: 0,
  size: 10,
  idCursor: null,
  updatedCursor: null,
  eventSource: null
};

const $ = (id) => document.getElementById(id);

const endpoints = {
  page: () => `/api/users/page?page=${state.page}&size=${state.size}&sort=${value("sort")}&direction=${value("direction")}`,
  search: () => {
    const params = new URLSearchParams({
      page: state.page,
      size: state.size,
      sort: value("sort"),
      direction: value("direction")
    });
    addParam(params, "emailContains", value("emailContains"));
    addParam(params, "status", value("status"));
    addParam(params, "city", value("city"));
    return `/api/users/search?${params}`;
  },
  idCursor: () => cursorUrl("/api/users/cursor/id", state.idCursor),
  updatedCursor: () => cursorUrl("/api/users/cursor/updated-at", state.updatedCursor)
};

document.addEventListener("DOMContentLoaded", () => {
  $("refresh").addEventListener("click", loadAll);
  $("count").addEventListener("click", loadCount);
  $("search").addEventListener("click", () => {
    state.page = 0;
    loadSearch();
  });
  $("loadPage").addEventListener("click", loadPage);
  $("idCursor").addEventListener("click", loadIdCursor);
  $("updatedCursor").addEventListener("click", loadUpdatedCursor);
  $("rawReport").addEventListener("click", loadRawReport);
  $("ndjson").addEventListener("click", loadNdjson);
  $("sse").addEventListener("click", loadSse);
  $("repositoryEmail").addEventListener("click", repositoryEmailLookup);
  $("prevPage").addEventListener("click", () => {
    state.page = Math.max(0, state.page - 1);
    loadSearch();
  });
  $("nextPage").addEventListener("click", () => {
    state.page += 1;
    loadSearch();
  });
  $("clear").addEventListener("click", clearForm);
  $("import").addEventListener("click", importUser);
  $("userForm").addEventListener("submit", saveUser);
  loadPage();
});

async function loadAll() {
  const data = await requestJson(`/api/users?sort=${value("sort")}&direction=${value("direction")}`);
  renderRows(data);
  writeOutput(data);
}

async function loadCount() {
  const count = await requestJson("/api/users/count");
  $("metric").textContent = `${count} visible users`;
  writeOutput({ count });
}

async function loadPage() {
  const data = await requestJson(endpoints.page());
  renderPage(data);
}

async function loadSearch() {
  const data = await requestJson(endpoints.search());
  renderPage(data);
}

async function loadIdCursor() {
  const data = await requestJson(endpoints.idCursor());
  state.idCursor = data.nextCursor;
  renderRows(data.content);
  $("metric").textContent = data.hasNext ? "Id cursor has another page" : "Id cursor reached the end";
  writeOutput(data);
}

async function loadUpdatedCursor() {
  const data = await requestJson(endpoints.updatedCursor());
  state.updatedCursor = data.nextCursor;
  renderRows(data.content);
  $("metric").textContent = data.hasNext ? "Updated cursor has another page" : "Updated cursor reached the end";
  writeOutput(data);
}

async function loadRawReport() {
  const data = await requestJson("/api/users/reports/status-counts?page=0&size=10");
  $("metric").textContent = "Status count report";
  renderReport(data.content);
  writeOutput(data);
}

async function loadNdjson() {
  const response = await fetch("/api/users/stream.ndjson");
  if (!response.ok || !response.body) {
    throw new Error(`NDJSON request failed with ${response.status}`);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const rows = [];
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop();
    for (const line of lines) {
      if (line.trim()) {
        rows.push(JSON.parse(line));
        renderRows(rows);
      }
    }
  }
  if (buffer.trim()) {
    rows.push(JSON.parse(buffer));
  }
  $("metric").textContent = `NDJSON streamed ${rows.length} users`;
  renderRows(rows);
  writeOutput(rows);
}

function loadSse() {
  if (state.eventSource) {
    state.eventSource.close();
  }
  const rows = [];
  state.eventSource = new EventSource("/api/users/stream.sse");
  state.eventSource.addEventListener("user", (event) => {
    rows.push(JSON.parse(event.data));
    renderRows(rows);
    $("metric").textContent = `SSE streamed ${rows.length} users`;
  });
  state.eventSource.onerror = () => {
    state.eventSource.close();
    writeOutput(rows);
  };
}

async function repositoryEmailLookup() {
  const email = $("email").value || "ava.ross@example.com";
  const data = await requestJson(`/api/users/repository/email/${encodeURIComponent(email)}`);
  renderRows(data ? [data] : []);
  writeOutput({
    note: "Derived repository methods are intentionally user-owned and are not rewritten by the library.",
    result: data
  });
}

async function saveUser(event) {
  event.preventDefault();
  const id = value("userId");
  const method = id ? "PUT" : "POST";
  const url = id ? `/api/users/${id}` : "/api/users";
  const data = await requestJson(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(formPayload(false))
  });
  clearForm();
  await loadPage();
  writeOutput(data);
}

async function importUser() {
  const data = await requestJson("/api/users/import", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(formPayload(true))
  });
  clearForm();
  await loadPage();
  writeOutput(data);
}

async function editUser(id) {
  const data = await requestJson(`/api/users/${id}/required`);
  $("userId").value = data.id;
  $("email").value = data.email;
  $("displayName").value = data.displayName;
  $("role").value = data.role;
  $("formStatus").value = data.status;
  $("formCity").value = data.city;
  writeOutput(data);
}

async function deleteUser(id) {
  const data = await requestJson(`/api/users/${id}`, { method: "DELETE" });
  await loadPage();
  writeOutput(data);
}

function formPayload(includeId) {
  return {
    id: includeId ? value("userId") : null,
    email: value("email"),
    displayName: value("displayName"),
    role: value("role"),
    status: value("formStatus"),
    city: value("formCity")
  };
}

function clearForm() {
  $("userId").value = "";
  $("email").value = "";
  $("displayName").value = "";
  $("role").value = "MEMBER";
  $("formStatus").value = "ACTIVE";
  $("formCity").value = "Rome";
}

function renderPage(page) {
  renderRows(page.content);
  $("pageState").textContent = `page ${page.page} of ${Math.max(page.totalPages - 1, 0)}`;
  $("metric").textContent = `${page.totalElements} total visible users`;
  writeOutput(page);
}

function renderRows(users) {
  $("rows").innerHTML = users.map((user) => `
    <tr>
      <td>${escapeHtml(user.email)}</td>
      <td>${escapeHtml(user.displayName)}</td>
      <td>${escapeHtml(user.role)}</td>
      <td>${escapeHtml(user.status)}</td>
      <td>${escapeHtml(user.city)}</td>
      <td>${formatDate(user.updatedAt)}</td>
      <td class="row-actions">
        <button class="secondary" onclick="editUser('${user.id}')">Edit</button>
        <button class="danger" onclick="deleteUser('${user.id}')">Delete</button>
      </td>
    </tr>
  `).join("");
}

function renderReport(rows) {
  $("rows").innerHTML = rows.map((row) => `
    <tr>
      <td>${escapeHtml(row.status)}</td>
      <td>${row.total}</td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
    </tr>
  `).join("");
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  if (response.status === 204) {
    return null;
  }
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    writeOutput(data);
    throw new Error(data?.message || `Request failed with ${response.status}`);
  }
  return data;
}

function cursorUrl(path, cursor) {
  const params = new URLSearchParams({
    limit: 10,
    direction: value("direction")
  });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return `${path}?${params}`;
}

function addParam(params, key, value) {
  if (value) {
    params.set(key, value);
  }
}

function value(id) {
  return $(id).value.trim();
}

function writeOutput(data) {
  $("output").textContent = JSON.stringify(data, null, 2);
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : "";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#039;");
}
