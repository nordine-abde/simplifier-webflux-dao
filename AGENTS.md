# AGENTS.md

Repository instructions for automated and assisted development of `simplifier-webflux-dao`.

## Project Context

This repository is a Java 25 R2DBC library. It provides explicit DAO-service helpers on top of Spring Data R2DBC. The first version intentionally does not override Spring Data repository internals.

Primary design documents:

- `docs/initial-design.md`
- `docs/implementation-tasks.md`
- `README.md`

Read these before implementing any task.

## Core Design Rules

- Keep Java 25 as the configured Java version.
- Use package root `com.anordine.simplifier.webflux.dao`.
- Keep repositories thin. Do not implement a custom `SimpleR2dbcRepository` base class in v1.
- Put lifecycle, read filtering, delete behavior, cursor pagination, and raw SQL helpers in DAO services.
- Apply soft-delete filtering only to DAO-owned read methods.
- Do not try to rewrite derived repository methods or user `@Query` methods.
- Soft-delete entities use fixed fields:
  - `deleted`
  - `deletedAt`, mapped to `deleted_at`
- Base entities use fixed timestamp fields:
  - `createdAt`, mapped to `created_at`
  - `updatedAt`, mapped to `updated_at`
- Delete methods exposed by DAO services return affected row counts.
- Soft delete must update rows directly from metadata-based SQL and must not fetch the entity first.
- Keep cursor pagination and streaming reads separate concepts.

## Implementation Workflow

For each task:

1. Re-read `docs/initial-design.md`.
2. Re-read `docs/implementation-tasks.md`.
3. Re-read `README.md`.
4. Read the current source and tests before editing.
5. Implement exactly the selected task.
6. Write or update focused tests.
7. Update `README.md` when the task changes public API, dependency usage, setup, examples, behavior, or limitations.
8. Run focused tests when useful, then run `./gradlew test`.
9. Commit only after tests pass.

## Editing Rules

- Keep changes scoped to the current task.
- Do not implement later tasks early unless a tiny support type is unavoidable.
- Do not revert unrelated user changes.
- Use existing project style when present.
- Use `apply_patch` for manual edits.
- Prefer `rg` for source search.
- Do not leave generated `org.example` sample code after task `T01`.

## Public Documentation

`README.md` is public-facing. Keep it useful for library consumers:

- Explain what the library does before explaining internals.
- Mark unreleased or not-yet-implemented features clearly while the project is in progress.
- Keep usage examples aligned with actual public APIs.
- Document limitations clearly, especially soft-delete filtering scope and user-owned repository queries.
