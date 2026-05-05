# dao-simplifier-webflux

`dao-simplifier-webflux` is a small Java library for Spring Data R2DBC applications that want a consistent DAO-service layer for common persistence behavior.

It focuses on explicit, testable DAO methods instead of replacing Spring Data repository internals. Repositories stay thin, while DAO services handle entity lifecycle timestamps, required reads, soft delete, count-returning deletes, classic pagination, cursor pagination, and streaming reads.

> Status: initial implementation in progress. T01 has prepared the real package structure and reactive R2DBC test foundation. T02 has added the reusable entity hierarchy. T03 has added thin repository marker interfaces. T04 has added configurable entity-not-found exceptions. DAO-service examples below describe the planned v1 API and will become available as later implementation phases land.

## Why This Library

Spring Data R2DBC gives you reactive repositories, but many applications still repeat the same DAO-level behavior:

- generate ids before insert
- set `created_at` and `updated_at`
- provide `findByIdRequired(...)`
- hide soft-deleted rows from standard reads
- perform soft delete without fetching the entity first
- return affected row counts from delete operations
- support both count-backed pages and efficient cursor pages

This library collects those patterns in reusable DAO services while leaving custom repository queries under application control.

## Design Overview

The library is built around three layers:

- Entity base classes for lifecycle timestamps and optional soft delete fields.
- Thin repository marker interfaces that extend Spring Data reactive repositories.
- Abstract DAO services that implement reusable persistence behavior.

The root package is:

```text
anordine.dao.simplifier.webflux
```

The first version deliberately avoids a custom `SimpleR2dbcRepository` replacement. Consumers keep using normal Spring Data R2DBC repositories.

## Requirements

- Java 25
- Spring Data R2DBC through Spring Boot's R2DBC starter
- A configured R2DBC connection in the consuming application

The project currently uses:

```gradle
api 'org.springframework.boot:spring-boot-starter-data-r2dbc'
```

Publication coordinates are not defined yet. Until the library is published, consume it as a local Gradle dependency or included build.

## Entity Model

Hard-delete entities extend `BaseEntity<ID>` or a concrete specialization such as `UuidEntity`.

Soft-delete entities extend `SoftDeleteEntity<ID>` or `SoftDeleteUuidEntity`.

These public base classes are currently available:

```text
anordine.dao.simplifier.webflux.entity.BaseEntity
anordine.dao.simplifier.webflux.entity.UuidEntity
anordine.dao.simplifier.webflux.entity.SoftDeleteEntity
anordine.dao.simplifier.webflux.entity.SoftDeleteUuidEntity
```

`BaseEntity<ID>` implements Spring Data `Persistable<ID>`. DAO services call `prePersist(...)` before saving and `markAsNotNew()` after a successful save so Spring Data R2DBC can choose insert or update correctly.

Soft-delete entities have fixed fields:

```text
deleted
deleted_at
```

All base entities have fixed timestamp fields:

```text
created_at
updated_at
```

Example:

```java
import anordine.dao.simplifier.webflux.entity.SoftDeleteUuidEntity;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public class UserEntity extends SoftDeleteUuidEntity {
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
```

Lifecycle behavior:

- `prePersist()` generates an id when absent.
- `createdAt` is set when preparing an insert.
- `updatedAt` is refreshed every time `prePersist(...)` runs.
- `prePersist(true)` forces an assigned-id insert and fails when the id is missing.
- `markAsNotNew()` clears the Spring Data insert flag after a successful save.

## Repositories

Repositories remain standard Spring Data R2DBC repositories, optionally using the library marker interfaces:

```java
import anordine.dao.simplifier.webflux.repository.SimplifiedR2dbcRepository;
import java.util.UUID;

public interface UserRepository
        extends SimplifiedR2dbcRepository<UserEntity, UUID> {
}
```

These public marker interfaces are currently available:

```text
anordine.dao.simplifier.webflux.repository.SimplifiedR2dbcRepository
anordine.dao.simplifier.webflux.repository.SimplifiedUuidR2dbcRepository
anordine.dao.simplifier.webflux.repository.SimplifiedSoftDeleteUuidR2dbcRepository
```

They add no lifecycle, soft-delete, or delete behavior beyond Spring Data's `ReactiveCrudRepository` contract. That behavior belongs to the DAO services.

No custom `@EnableR2dbcRepositories(repositoryBaseClass = ...)` configuration is required for v1.

## Planned DAO Services

Applications create concrete services by extending the abstract DAO service and passing the entity class explicitly.

```java
import anordine.dao.simplifier.webflux.service.AbstractDaoService;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class UserDaoService
        extends AbstractDaoService<UserEntity, UserRepository, UUID> {

    public UserDaoService(
            UserRepository repository,
            R2dbcEntityTemplate template
    ) {
        super(repository, template, UserEntity.class);
    }
}
```

The DAO service owns common methods such as:

```java
dao.save(user);
dao.findById(id);
dao.findByIdRequired(id);
dao.findAll();
dao.count();
dao.deleteById(id);
```

Delete methods return affected row counts:

```java
Mono<Long> deletedRows = dao.deleteById(id);
```

For soft-delete entities, `deleteById(...)` updates `deleted`, `deleted_at`, and `updated_at` directly without fetching the entity first.

## Soft Delete Scope

Soft-delete filtering applies only to methods implemented by the library DAO service.

These are application responsibility:

- derived repository methods such as `findByEmail(...)`
- custom repository methods
- SQL written with `@Query`
- raw SQL passed to helper methods

If those queries target soft-delete tables, include `deleted = false` yourself.

## Pagination

The library supports three separate read styles.

Classic pages are useful for admin screens that need total counts:

```java
Mono<Page<UserEntity>> page = dao.findAllByCriteria(criteria, pageable);
```

Cursor pages are useful for efficient seek pagination:

```java
Mono<CursorPage<UserEntity>> page =
        dao.findAllByUpdatedAtCursor(cursorUpdatedAt, cursorId, 50, Sort.Direction.DESC);
```

Streaming reads return `Flux<T>` and are meant for endpoints or pipelines that genuinely stream rows:

```java
Flux<UserEntity> users = dao.streamAll();
```

Returning `Flux<T>` from a controller does not automatically mean the HTTP response is Server-Sent Events. Use an appropriate media type such as `application/x-ndjson` or `text/event-stream` when streaming behavior is required.

## Exceptions

Required-read methods such as the planned `findByIdRequired(...)` use a configurable exception factory. The exception API is currently available:

```text
anordine.dao.simplifier.webflux.exception.EntityNotFoundException
anordine.dao.simplifier.webflux.exception.EntityNotFoundExceptionFactory
anordine.dao.simplifier.webflux.exception.DefaultEntityNotFoundExceptionFactory
```

By default, the library throws `EntityNotFoundException` with a message containing the entity simple name and id. Applications can provide their own `EntityNotFoundExceptionFactory` to map missing rows to domain-specific runtime exceptions.

## Development

Run tests:

```bash
./gradlew test
```

The test suite is set up with JUnit Jupiter, Reactor Test, and an in-memory R2DBC H2 driver for focused DAO tests as implementation phases are added.

List implementation phases:

```bash
scripts/run-implementation-tasks.sh list
```

Run one phase through Codex automation:

```bash
scripts/run-implementation-tasks.sh T01
```

Run all phases:

```bash
scripts/run-implementation-tasks.sh all
```

The automation requires a clean git worktree before each phase, runs tests after Codex finishes, and commits the phase if verification passes.

## Current Limitations

The current codebase contains the package/test foundation, reusable entity hierarchy, repository marker interfaces, and configurable entity-not-found exceptions. DAO services, metadata helpers, pagination, streaming reads, and delete behavior are still planned for later phases.

The v1 design does not include:

- custom repository base-class overrides
- automatic rewriting of derived repository methods
- automatic rewriting of `@Query` SQL
- configurable soft-delete field names
- arbitrary multi-column cursor pagination
- multi-column ids
- auditing integration
- optimistic locking
