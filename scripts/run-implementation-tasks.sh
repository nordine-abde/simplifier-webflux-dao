#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CODEX_BIN="${CODEX_BIN:-codex}"
TEST_COMMAND="${TEST_COMMAND:-./gradlew test}"
LOG_DIR="${LOG_DIR:-${TMPDIR:-/tmp}/simplifier-webflux-dao-codex-task-logs}"

TASK_IDS=(
  T01
  T02
  T03
  T04
  T05
  T06
  T07
  T08
  T09
  T10
  T11
  T12
  T13
  T14
)

declare -A TASK_TITLES=(
  [T01]="Test Foundation And Package Cleanup"
  [T02]="Base Entity Model"
  [T03]="Repository Marker Interfaces"
  [T04]="Not Found Exceptions"
  [T05]="Entity Metadata Resolver"
  [T06]="DAO Service Save And Basic Reads"
  [T07]="Delete Operations"
  [T08]="Criteria And Classic Page Reads"
  [T09]="Cursor Page Infrastructure"
  [T10]="Id Cursor Pagination"
  [T11]="Updated At Plus Id Cursor Pagination"
  [T12]="Streaming Reads"
  [T13]="Raw SQL Page Helper"
  [T14]="Public API Polish And Documentation"
)

declare -A COMMIT_MESSAGES=(
  [T01]="Prepare R2DBC library test foundation"
  [T02]="Add reusable R2DBC entity hierarchy"
  [T03]="Add simplified R2DBC repository markers"
  [T04]="Add configurable entity not found exceptions"
  [T05]="Add R2DBC entity metadata resolver"
  [T06]="Implement DAO save and basic read methods"
  [T07]="Implement hard and soft delete DAO operations"
  [T08]="Add criteria and classic page DAO reads"
  [T09]="Add cursor pagination primitives"
  [T10]="Implement id cursor DAO pagination"
  [T11]="Implement updated-at cursor DAO pagination"
  [T12]="Add explicit DAO streaming reads"
  [T13]="Add raw SQL page helper"
  [T14]="Document DAO simplifier public API"
)

usage() {
  cat <<'EOF'
Usage:
  scripts/run-implementation-tasks.sh list
  scripts/run-implementation-tasks.sh all
  scripts/run-implementation-tasks.sh T01 [T02 ...]

Environment:
  CODEX_BIN      Codex executable. Default: codex
  TEST_COMMAND   Verification command after each task. Default: ./gradlew test
  LOG_DIR        Directory for Codex task logs. Default: /tmp/simplifier-webflux-dao-codex-task-logs

The script requires a clean git worktree before each task so each phase can be
committed independently. Codex runs as:
codex --yolo exec -C <repo> "<prompt>"
EOF
}

ensure_clean_worktree() {
  if [[ -n "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
    echo "Refusing to start because the git worktree is not clean."
    echo "Commit or stash current changes, then rerun this script."
    git -C "$ROOT_DIR" status --short
    exit 1
  fi
}

print_tasks() {
  for task_id in "${TASK_IDS[@]}"; do
    printf '%s - %s\n' "$task_id" "${TASK_TITLES[$task_id]}"
  done
}

task_exists() {
  local requested="$1"
  local task_id
  for task_id in "${TASK_IDS[@]}"; do
    [[ "$requested" == "$task_id" ]] && return 0
  done
  return 1
}

run_task() {
  local task_id="$1"
  local title="${TASK_TITLES[$task_id]}"
  local commit_message="${COMMIT_MESSAGES[$task_id]}"
  local log_file="$LOG_DIR/${task_id}.log"
  local prompt

  ensure_clean_worktree
  mkdir -p "$LOG_DIR"

  prompt="$(cat <<EOF
You are implementing one phase of simplifier-webflux-dao.

Task: $task_id - $title

Required workflow:
1. Re-read AGENTS.md.
2. Re-read the full documentation in docs/initial-design.md.
3. Re-read the full task list in docs/implementation-tasks.md.
4. Re-read README.md.
5. Locate the section for task $task_id and implement only that task.
6. Read the current source and tests before editing.
7. Write or update focused tests for this task.
8. Update README.md when this task changes public API, setup, examples, behavior, limitations, or implementation status.
9. Run the focused tests when useful, then run: $TEST_COMMAND
10. Confirm the implementation matches the design and tests pass.
11. Do not commit. The runner script will commit this phase programmatically after its own verification passes.

Constraints:
- Keep Java 25.
- Use package root com.anordine.simplifier.webflux.dao.
- Do not implement later tasks unless a tiny support type is unavoidable for this task.
- Do not revert unrelated user changes.
- Do not leave generated org.example sample code once task T01 is complete.
- Keep changes scoped and production-ready.
EOF
)"

  echo "==> Running $task_id - $title"
  "$CODEX_BIN" --yolo exec -C "$ROOT_DIR" "$prompt" 2>&1 | tee "$log_file"

  echo "==> Verifying $task_id with: $TEST_COMMAND"
  (cd "$ROOT_DIR" && eval "$TEST_COMMAND")

  if [[ -z "$(git -C "$ROOT_DIR" status --porcelain)" ]]; then
    echo "==> No changes produced for $task_id; skipping commit."
    return 0
  fi

  git -C "$ROOT_DIR" add -A
  git -C "$ROOT_DIR" commit -m "$commit_message"
  echo "==> Committed $task_id: $commit_message"
}

main() {
  if [[ $# -eq 0 ]]; then
    usage
    exit 1
  fi

  case "$1" in
    -h|--help|help)
      usage
      exit 0
      ;;
    list)
      print_tasks
      exit 0
      ;;
    all)
      shift
      if [[ $# -ne 0 ]]; then
        echo "'all' does not accept additional task ids."
        exit 1
      fi
      for task_id in "${TASK_IDS[@]}"; do
        run_task "$task_id"
      done
      ;;
    *)
      local task_id
      for task_id in "$@"; do
        if ! task_exists "$task_id"; then
          echo "Unknown task: $task_id"
          echo
          print_tasks
          exit 1
        fi
      done
      for task_id in "$@"; do
        run_task "$task_id"
      done
      ;;
  esac
}

main "$@"
