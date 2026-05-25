# Users Example App

This Spring WebFlux application demonstrates `simplifier-webflux-dao` against PostgreSQL.

It uses:

- `SoftDeleteUuidEntity`
- `SimplifiedSoftDeleteUuidR2dbcRepository`
- `AbstractSoftDeleteUuidDaoService`
- custom `findByIdRequired(...)` exception factory
- DAO-owned CRUD, soft delete, count, sorting, classic pages, criteria pages, id cursor pages, `updated_at + id` cursor pages, raw SQL page projections, NDJSON streaming, and SSE streaming
- Spring Data derived repository methods as user-owned queries
- Liquibase schema and 40 seeded users
- a static browser UI served from `/`

## Run

From the repository root:

```bash
docker compose -f examples/users-app/compose.yaml up -d
./gradlew :examples:users-app:bootRun
```

Open:

```text
http://localhost:8080
```

Spring Boot Docker Compose support is also on the classpath for development. The explicit `docker compose` command is still the most predictable way to start the database before running the app.

## Useful Endpoints

```text
POST   /api/users
POST   /api/users/import
PUT    /api/users/{id}
GET    /api/users/{id}
GET    /api/users/{id}/required
GET    /api/users
GET    /api/users/page
GET    /api/users/search
GET    /api/users/cursor/id
GET    /api/users/cursor/id/after/{id}
GET    /api/users/cursor/updated-at
GET    /api/users/stream.ndjson
GET    /api/users/stream.sse
GET    /api/users/reports/status-counts
DELETE /api/users/{id}
DELETE /api/users?ids={id1}&ids={id2}
DELETE /api/users/all
```

Repository-derived endpoint examples are exposed under `/api/users/repository/...`. They are included to show the library boundary: DAO-owned reads filter soft-deleted rows, while derived repository methods remain application responsibility.
