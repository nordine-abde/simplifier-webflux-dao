# Implementation Tasks

This is the phased implementation backlog for `dao-simplifier-webflux`.

Each task is intended to be implemented independently by an automated Codex run. Before starting any task, re-read:

- `docs/initial-design.md`
- `docs/implementation-tasks.md`
- the current source and test tree

Every task must include implementation, focused tests, and a successful Gradle test run before commit.

## Task List

### T01 - Test Foundation And Package Cleanup

Goal: prepare the library for real R2DBC implementation and tests.

Scope:

- Remove the generated `org.example` sample library and sample test.
- Create the root package structure under `lib/src/main/java/anordine/dao/simplifier/webflux`.
- Create matching test package structure under `lib/src/test/java/anordine/dao/simplifier/webflux`.
- Add test dependencies needed for reactive R2DBC tests, such as Reactor test support and an in-memory R2DBC driver.
- Keep Java 25 as the configured toolchain.
- Add minimal smoke tests proving the test runtime starts and Gradle can execute the test suite.

Expected verification:

- `./gradlew test`

Commit message:

```text
Prepare R2DBC library test foundation
```

### T02 - Base Entity Model

Goal: implement the reusable entity hierarchy.

Scope:

- Add `BaseEntity<ID>` implementing Spring Data `Persistable<ID>`.
- Add `UuidEntity`.
- Add `SoftDeleteEntity<ID>`.
- Add `SoftDeleteUuidEntity`.
- Use fixed property and column names from `docs/initial-design.md`.
- Implement lifecycle behavior:
  - generated id when id is absent
  - `createdAt` set only for inserts
  - `updatedAt` set on every save preparation
  - assigned-id insert via `prePersist(true)`
  - `markAsNotNew()`
- Keep the API usable by subclasses and Spring Data R2DBC.

Expected tests:

- id generation for UUID entities
- timestamp initialization
- update timestamp refresh without resetting `createdAt`
- assigned-id insert behavior
- invalid assigned-id insert when id is missing
- soft-delete defaults

Expected verification:

- `./gradlew test`

Commit message:

```text
Add reusable R2DBC entity hierarchy
```

### T03 - Repository Marker Interfaces

Goal: add thin repository interfaces without custom repository behavior.

Scope:

- Add `SimplifiedR2dbcRepository<E, ID>`.
- Add `SimplifiedUuidR2dbcRepository<E>`.
- Add `SimplifiedSoftDeleteUuidR2dbcRepository<E>` if it helps the type model.
- Ensure repository interfaces extend Spring Data reactive repository contracts only.
- Do not implement custom repository base-class overrides.

Expected tests:

- compile-time tests through minimal test repositories or fixture repositories
- no behavior beyond Spring Data repository compatibility

Expected verification:

- `./gradlew test`

Commit message:

```text
Add simplified R2DBC repository markers
```

### T04 - Not Found Exceptions

Goal: provide configurable required-read exceptions.

Scope:

- Add `EntityNotFoundException`.
- Add `EntityNotFoundExceptionFactory`.
- Add a default factory implementation.
- Keep domain-specific exception naming out of the library.

Expected tests:

- default exception message includes entity simple name and id
- custom factory can create a caller-defined runtime exception

Expected verification:

- `./gradlew test`

Commit message:

```text
Add configurable entity not found exceptions
```

### T05 - Entity Metadata Resolver

Goal: centralize Spring Data R2DBC metadata access.

Scope:

- Add a metadata helper that resolves:
  - table name
  - id property and id column
  - `createdAt` column
  - `updatedAt` column
  - soft-delete columns for soft-delete entities
- Use Spring Data relational mapping metadata from `R2dbcEntityTemplate` and its converter.
- Detect whether an entity is soft-delete capable with `SoftDeleteEntity.class.isAssignableFrom(entityClass)`.
- Fail fast for missing required mapped properties.
- Keep metadata quoting and column/table rendering compatible with Spring Data SQL identifiers as much as practical.

Expected tests:

- metadata resolves table and id column for a hard-delete fixture entity
- metadata resolves fixed soft-delete columns for a soft-delete fixture entity
- missing soft-delete metadata fails with a clear exception if such a fixture is practical

Expected verification:

- `./gradlew test`

Commit message:

```text
Add R2DBC entity metadata resolver
```

### T06 - DAO Service Save And Basic Reads

Goal: implement the first usable `AbstractDaoService` methods.

Scope:

- Add `AbstractDaoService<E, R, ID>` with explicit `Class<E> entityClass` constructor argument.
- Inject repository, `R2dbcEntityTemplate`, entity class, and exception factory.
- Add default constructor path using the default exception factory if practical.
- Implement:
  - `save(E entity)`
  - `save(E entity, boolean asNewWithId)`
  - `saveAll(Collection<E> entities)`
  - `saveAll(Collection<E> entities, boolean asNewWithId)`
  - `findById(ID id)`
  - `findByIdRequired(ID id)`
  - `existsById(ID id)`
  - `count()`
  - `findAll()`
  - `findAllByIds(Collection<ID> ids)`
- Apply soft-delete filtering to DAO-owned reads.
- Mark saved entities as not new after successful saves.
- Add transactional annotations as described in the design.

Expected tests:

- save inserts hard-delete and soft-delete fixture entities
- save updates existing entities
- saveAll prepares and persists all entities
- find-by-id reads hard-delete rows
- find-by-id ignores soft-deleted rows
- required find throws default and custom exceptions
- exists/count/findAll/findAllByIds ignore soft-deleted rows

Expected verification:

- `./gradlew test`

Commit message:

```text
Implement DAO save and basic read methods
```

### T07 - Delete Operations

Goal: implement count-returning hard delete and soft delete methods.

Scope:

- Implement:
  - `delete(E entity)`
  - `deleteById(ID id)`
  - `deleteAll()`
  - `deleteAllByIds(Collection<ID> ids)`
- For hard-delete entities, execute metadata-based physical delete SQL and return affected row count.
- For soft-delete entities, execute metadata-based update SQL without fetching the entity:
  - set `deleted = true`
  - set `deleted_at = Instant.now()`
  - set `updated_at = Instant.now()`
  - include `deleted = false` in the predicate
- Return `Mono<Long>`.
- Handle empty id collections without issuing invalid SQL.

Expected tests:

- hard `deleteById` physically removes one row and returns count
- hard `deleteAllByIds` removes matching rows
- hard `deleteAll` removes all rows
- soft `deleteById` updates flags and timestamps without requiring a prior fetch
- soft delete methods do not double-count rows already deleted
- DAO reads hide soft-deleted rows after delete

Expected verification:

- `./gradlew test`

Commit message:

```text
Implement hard and soft delete DAO operations
```

### T08 - Criteria And Classic Page Reads

Goal: add criteria-based reads and classic count-backed pagination.

Scope:

- Implement `findAllByCriteria(Criteria criteria, Pageable pageable)`.
- Consider adding `findAll(Sort sort)` and `findAll(Pageable pageable)` if they fit cleanly.
- Combine caller criteria with `deleted = false` for soft-delete entities.
- Use `R2dbcEntityTemplate.count(...)` for total.
- Use `R2dbcEntityTemplate.select(...)` for page content.
- Return Spring Data `PageImpl`.

Expected tests:

- criteria filters hard-delete rows
- criteria filters soft-delete rows and excludes deleted rows
- pageable content and total count are correct
- sorting is honored for implemented page/sort methods

Expected verification:

- `./gradlew test`

Commit message:

```text
Add criteria and classic page DAO reads
```

### T09 - Cursor Page Infrastructure

Goal: add generic cursor result and encoding primitives.

Scope:

- Add `CursorPage<T>`.
- Add cursor value types for:
  - id cursor
  - `updated_at + id` cursor
- Add a cursor codec that treats cursor strings as opaque public values.
- Encode/decode payloads with a stable type discriminator.
- Prefer Base64-url encoded JSON if the project already has suitable JSON support; otherwise use a compact internal format but keep the public cursor opaque.
- Validate malformed cursors with clear exceptions.

Expected tests:

- cursor page exposes content, next cursor, and has-next state
- id cursor round-trips
- updated-at plus id cursor round-trips
- malformed cursor fails clearly
- cursor type mismatch fails clearly

Expected verification:

- `./gradlew test`

Commit message:

```text
Add cursor pagination primitives
```

### T10 - Id Cursor Pagination

Goal: implement id-based seek pagination in the DAO service.

Scope:

- Implement an id cursor DAO method matching the design.
- Support ascending and descending order.
- Apply soft-delete filtering for soft-delete entities.
- Fetch `limit + 1` rows.
- Return `Mono<CursorPage<E>>`.
- Generate `nextCursor` from the last returned entity only when another page exists.
- Validate limit values.

Expected tests:

- first page ascending
- next page ascending
- first page descending
- next page descending
- no next cursor on final page
- soft-deleted rows excluded
- invalid limit rejected

Expected verification:

- `./gradlew test`

Commit message:

```text
Implement id cursor DAO pagination
```

### T11 - Updated At Plus Id Cursor Pagination

Goal: implement deterministic updated-at seek pagination.

Scope:

- Implement an `updated_at + id` cursor DAO method matching the design.
- Support ascending and descending order.
- Apply soft-delete filtering for soft-delete entities.
- Fetch `limit + 1` rows.
- Return `Mono<CursorPage<E>>`.
- Generate the next cursor from the last returned entity when another page exists.
- Use id as deterministic tie-breaker when rows share the same `updatedAt`.
- Validate limit and cursor consistency.

Expected tests:

- first page ascending
- next page ascending
- first page descending
- next page descending
- deterministic order for equal `updatedAt`
- no next cursor on final page
- soft-deleted rows excluded
- invalid limit rejected

Expected verification:

- `./gradlew test`

Commit message:

```text
Implement updated-at cursor DAO pagination
```

### T12 - Streaming Reads

Goal: add explicit streaming methods separate from cursor pagination.

Scope:

- Implement:
  - `streamAll()`
  - `streamAllByCriteria(Criteria criteria, Sort sort)`
- Return `Flux<E>` directly from `R2dbcEntityTemplate`.
- Do not collect the stream inside the DAO.
- Apply soft-delete filtering for soft-delete entities.
- Add Javadocs explaining that HTTP streaming depends on endpoint media type such as NDJSON or SSE.

Expected tests:

- streaming returns all hard-delete rows
- streaming excludes soft-deleted rows
- criteria and sort are honored
- tests consume with StepVerifier without requiring DAO-side collection

Expected verification:

- `./gradlew test`

Commit message:

```text
Add explicit DAO streaming reads
```

### T13 - Raw SQL Page Helper

Goal: add DTO projection pagination for caller-owned SQL.

Scope:

- Implement `findPage(...)` for raw SQL DTO projections.
- Bind non-null and null parameters.
- Apply `Pageable` limit and offset.
- Return `Mono<Page<T>>`.
- Document that the helper does not rewrite SQL and does not inject soft-delete predicates.
- Avoid unsafe sort appending unless a minimal safe implementation is available; document limitations clearly.

Expected tests:

- maps DTO rows with a supplied mapper
- total count is returned
- pageable limit and offset are honored
- parameters are bound
- null parameters are handled or documented with a failing/unsupported behavior test

Expected verification:

- `./gradlew test`

Commit message:

```text
Add raw SQL page helper
```

### T14 - Public API Polish And Documentation

Goal: finalize the first implementation pass.

Scope:

- Add or refine Javadocs for all public types and methods listed in `docs/initial-design.md`.
- Ensure package names, method names, and behavior match the design.
- Ensure no generated sample code remains.
- Add a concise README usage section if no README exists, or update the existing README.
- Review tests for redundant fixtures and consolidate only when it improves clarity.
- Run the full test suite.

Expected tests:

- all existing tests pass
- no additional behavior tests required unless documentation review finds a gap

Expected verification:

- `./gradlew test`

Commit message:

```text
Document DAO simplifier public API
```

## Automation Rules

Automated task runners should follow these rules:

- Work on exactly one task per Codex invocation.
- Re-read the full documentation before making changes.
- Respect all design decisions in `docs/initial-design.md`.
- Keep changes scoped to the selected task.
- Do not implement future tasks early except for tiny support code that is impossible to avoid.
- Write or update tests in the same task.
- Run the focused tests first when useful, then run `./gradlew test`.
- Do not revert unrelated user changes.
- Commit only after tests pass.
