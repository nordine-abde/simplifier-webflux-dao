# Initial Design

This document is the implementation guideline for `dao-simplifier-webflux`.

The library will provide a small DAO-service abstraction on top of Spring Data R2DBC. It is intentionally not a custom `SimpleR2dbcRepository` replacement for the first version. The goal is to keep behavior explicit, easy to test, and independent from Spring Data repository internals.

## Scope

The library is responsible for the methods it implements in its DAO services. It must not try to intercept or rewrite user-defined repository methods, derived queries, or `@Query` SQL.

In practice:

- Library DAO methods must handle lifecycle timestamps.
- Library DAO methods must filter soft-deleted rows when reading soft-delete entities.
- Library DAO methods must soft-delete or hard-delete based on the entity base class.
- User repository methods such as `findByEmail(...)` are user responsibility.
- User `@Query` methods are user responsibility.

## Package

Use this root package:

```text
anordine.dao.simplifier.webflux
```

Recommended subpackages:

```text
anordine.dao.simplifier.webflux.entity
anordine.dao.simplifier.webflux.repository
anordine.dao.simplifier.webflux.service
anordine.dao.simplifier.webflux.exception
```

## Dependencies

For now the library uses the Spring Boot R2DBC starter:

```gradle
api 'org.springframework.boot:spring-boot-starter-data-r2dbc'
```

The project currently imports Spring Boot dependency management and uses Java 25.

This can be revisited later for publication. A leaner published library may prefer `spring-data-r2dbc`, Reactor, and Spring transaction dependencies directly instead of a Boot starter.

## Consumer Configuration

Consumers should keep using Spring Data repositories normally. They do not need a custom repository base class.

Example consumer repository:

```java
public interface UserRepository extends SimplifiedR2dbcRepository<UserEntity, UUID> {
}
```

or, if the library repository base is only a marker around Spring's repository:

```java
public interface UserRepository extends ReactiveCrudRepository<UserEntity, UUID> {
}
```

No custom `@EnableR2dbcRepositories(repositoryBaseClass = ...)` is required for the DAO-service v1 design.

If a future custom repository implementation is added, document this activation pattern:

```java
@EnableR2dbcRepositories(repositoryBaseClass = ...)
```

but do not implement that in v1.

## Entity Model

### Base Entity

Create a base abstract entity for hard-delete behavior:

```java
public abstract class BaseEntity<ID> implements Persistable<ID> {
    @Id
    @Column("id")
    protected ID id;

    @Column("created_at")
    protected Instant createdAt;

    @Column("updated_at")
    protected Instant updatedAt;

    @Transient
    protected boolean isNew;

    public void prePersist() {
        prePersist(false);
    }

    public void prePersist(boolean asNewWithId) {
        ...
    }

    public void markAsNotNew() {
        this.isNew = false;
    }

    protected abstract ID generateId();
}
```

Behavior:

- `createdAt` is set only when the entity is inserted as new.
- `updatedAt` is set on every save.
- `isNew` controls Spring Data R2DBC insert vs update through `Persistable`.
- If `id == null`, `prePersist` generates one through `generateId()`.
- If `asNewWithId == true` and `id == null`, throw `IllegalArgumentException`.
- If `asNewWithId == true` and `id != null`, force `isNew = true`.
- After successful save, DAO methods must call `markAsNotNew()`.

### UUID Entity

Create a UUID specialization. UUID specializations generate UUIDv7 identifiers
by default so database UUID primary-key indexes get timestamp-first insert
locality:

```java
public abstract class UuidEntity extends BaseEntity<UUID> {
    @Override
    protected UUID generateId() {
        return UuidV7Generator.generate();
    }
}
```

### Soft Delete Entity

Create a soft-delete base class:

```java
public abstract class SoftDeleteEntity<ID> extends BaseEntity<ID> {
    @Column("deleted")
    protected boolean deleted;

    @Column("deleted_at")
    protected Instant deletedAt;
}
```

Create a UUID specialization if useful:

```java
public abstract class SoftDeleteUuidEntity extends SoftDeleteEntity<UUID> {
    @Override
    protected UUID generateId() {
        return UuidV7Generator.generate();
    }
}
```

Soft-delete field names are fixed:

- Java property: `deleted`
- Column: `deleted`
- Java property: `deletedAt`
- Column: `deleted_at`

The base entity field names are fixed:

- Java property: `createdAt`
- Column: `created_at`
- Java property: `updatedAt`
- Column: `updated_at`

## Repository Model

Keep repositories thin.

Base repository:

```java
public interface SimplifiedR2dbcRepository<E extends BaseEntity<ID>, ID>
        extends ReactiveCrudRepository<E, ID> {
}
```

UUID repository:

```java
public interface SimplifiedUuidR2dbcRepository<E extends UuidEntity>
        extends SimplifiedR2dbcRepository<E, UUID> {
}
```

Soft-delete UUID repository can be added only if it helps type signatures:

```java
public interface SimplifiedSoftDeleteUuidR2dbcRepository<E extends SoftDeleteUuidEntity>
        extends SimplifiedR2dbcRepository<E, UUID> {
}
```

Repositories should not contain lifecycle or delete behavior. That belongs to DAO services.

## DAO Service Model

Create an abstract DAO service:

```java
public abstract class AbstractDaoService<
        E extends BaseEntity<ID>,
        R extends SimplifiedR2dbcRepository<E, ID>,
        ID> {
}
```

Constructor dependencies:

```java
protected AbstractDaoService(
        R repository,
        R2dbcEntityTemplate template,
        Class<E> entityClass,
        EntityNotFoundExceptionFactory exceptionFactory
) {
}
```

Do not infer `entityClass` with fragile generic reflection. Require subclasses to pass it explicitly:

```java
super(repository, template, UserEntity.class, exceptionFactory);
```

This avoids the `getGenericSuperclass()` limitation from the source repo and works better with proxies/subclasses.

### Transaction Boundaries

DAO methods should use Spring transactions:

- Write methods: `@Transactional`
- Read methods: `@Transactional(readOnly = true)`

This mirrors the source project and keeps behavior expected in WebFlux/R2DBC applications.

## DAO Methods

### Save

Expose:

```java
public Mono<E> save(E entity)
public Mono<E> save(E entity, boolean asNewWithId)
public Flux<E> saveAll(Collection<E> entities)
public Flux<E> saveAll(Collection<E> entities, boolean asNewWithId)
```

Behavior:

- Call `entity.prePersist(asNewWithId)` before saving.
- Delegate to `repository.save(entity)` / `repository.saveAll(entities)`.
- Mark saved entities as not new.
- Return the saved entity/entities.

The overloaded `save(entity, boolean asNewWithId)` is allowed because the DAO service owns the API. It avoids fighting Spring repository method signatures.

### Find By Id

Expose:

```java
public Mono<E> findById(ID id)
public Mono<E> findByIdRequired(ID id)
```

Behavior for hard-delete entities:

- `findById(id)` delegates to `repository.findById(id)`.

Behavior for soft-delete entities:

- `findById(id)` must return only rows where `deleted = false`.
- Implement with `R2dbcEntityTemplate` and `Query`, not with `repository.findById`, because the repository method does not know soft-delete rules.

Example query shape:

```java
Query.query(
    Criteria.where("id").is(id)
        .and("deleted").is(false)
)
```

`findByIdRequired(id)`:

- Calls `findById(id)`.
- If empty, returns `Mono.error(...)` using the configured exception factory.

### Exists By Id

Expose:

```java
public Mono<Boolean> existsById(ID id)
```

Behavior for hard-delete entities:

- Delegate to `repository.existsById(id)`.

Behavior for soft-delete entities:

- Return true only if a row exists with `id = :id and deleted = false`.

### Count

Expose:

```java
public Mono<Long> count()
```

Behavior for hard-delete entities:

- Delegate to `repository.count()`.

Behavior for soft-delete entities:

- Count only rows where `deleted = false`.

### Find All

Expose at least:

```java
public Flux<E> findAll()
public Flux<E> findAllByIds(Collection<ID> ids)
```

Behavior for soft-delete entities:

- Filter `deleted = false`.
- `findAllByIds` must apply both `id in (...)` and `deleted = false`.

Consider adding:

```java
public Flux<E> findAll(Sort sort)
public Mono<Page<E>> findAll(Pageable pageable)
```

If implemented, soft-delete filters still apply.

### Criteria Page

Expose:

```java
public Mono<Page<E>> findAllByCriteria(Criteria criteria, Pageable pageable)
```

Behavior:

- For hard-delete entities, apply the supplied criteria as-is.
- For soft-delete entities, combine the supplied criteria with `deleted = false`.
- Use `R2dbcEntityTemplate.count(...)` for total.
- Use `R2dbcEntityTemplate.select(...)` for content.
- Return `PageImpl`.

Implementation detail:

```java
Criteria effectiveCriteria = applySoftDeleteCriteria(criteria);
Query pageQuery = Query.query(effectiveCriteria).with(pageable);
Mono<Long> count = template.count(Query.query(effectiveCriteria), entityClass);
Flux<E> results = template.select(pageQuery, entityClass);
```

### Cursor Pagination

Add cursor pagination as a first-class DAO feature.

Keep both pagination models:

- `Mono<Page<T>>` remains available for admin screens and other views that need total counts.
- `Mono<CursorPage<T>>` is added for efficient seek pagination.
- `Flux<T>` streaming methods are separate and should be used only for endpoints that genuinely stream rows.

Cursor pagination is not the same thing as returning `Flux<T>` from an endpoint. Cursor pagination returns a bounded page plus metadata for the next request. Streaming returns a sequence of rows and depends on the HTTP media type, such as NDJSON or SSE.

#### Cursor Page Type

Create a simple result type:

```java
public record CursorPage<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext
) {
}
```

Rules:

- Fetch `limit + 1` rows.
- Return at most `limit` rows in `content`.
- `hasNext = fetchedRows.size() > limit`.
- `nextCursor` is generated from the last returned row when `hasNext` is true.
- `nextCursor` is `null` when there is no next page.

The DAO may collect a bounded `limit + 1` list in memory. This is acceptable because cursor pagination never collects an unbounded result set.

#### Cursor Encoding

Cursors should be opaque to clients.

DAO methods should accept the opaque cursor string returned by
`CursorPage.nextCursor()` for the next-page call. Typed cursor methods can
remain available as lower-level helpers, but application clients should not
need to decode cursor strings themselves.

Recommended v1 shape:

- Encode cursor payload as JSON.
- Base64-url encode the JSON string.
- Include a cursor `type` field so different cursor shapes are explicit.

Example id cursor payload:

```json
{
  "type": "ID",
  "id": "6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416"
}
```

Example updated-at cursor payload:

```json
{
  "type": "UPDATED_AT_ID",
  "updatedAt": "2026-05-05T10:15:30Z",
  "id": "6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416"
}
```

If JSON support is not desired in the core library, use a small internal delimiter format for v1, but keep the public cursor string opaque so the encoding can change later.

Because the DAO service is generic over `ID`, id cursor encoding must not rely
on arbitrary `toString()` values. Provide a `CursorIdCodec<ID>` with explicit
encoder and decoder functions. The library can include default codecs for
common id types such as UUID, String, Long, and Integer. UUID convenience DAO
services should configure the UUID codec automatically.

#### Id Cursor

Support id-based cursor pagination for simple stable ordering by id.

Expose:

```java
public Mono<CursorPage<E>> findAllByIdCursor(
        int limit,
        Sort.Direction direction
)

public Mono<CursorPage<E>> findAllByIdCursor(
        String cursor,
        int limit,
        Sort.Direction direction
)

public Mono<CursorPage<E>> findAllByIdCursorAfterId(
        ID cursorId,
        int limit,
        Sort.Direction direction
)
```

Behavior:

- `findAllByIdCursor(limit, direction)` reads the first page.
- `findAllByIdCursor(cursor, limit, direction)` decodes the opaque cursor string inside the library.
- `findAllByIdCursorAfterId(cursorId, limit, direction)` is the lower-level typed helper.
- If `direction == ASC`, query `id > :cursorId`.
- If `direction == DESC`, query `id < :cursorId`.
- Always order by id in the selected direction.
- For soft-delete entities, also apply `deleted = false`.
- Fetch `limit + 1`.

Conceptual SQL for ascending order:

```sql
select *
from <table>
where (:cursorId is null or <id_column> > :cursorId)
  and deleted = false
order by <id_column> asc
limit :limitPlusOne
```

The `deleted = false` predicate only applies to soft-delete entities.

Use this cursor only when id ordering is acceptable for the consumer.

#### Updated At Plus Id Cursor

Support cursor pagination ordered by `updated_at` plus `id`.

This is the default recommended cursor for most mutable entities because `updated_at` maps naturally to "recently changed" views, while `id` provides a deterministic tie-breaker.

Expose:

```java
public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
        int limit,
        Sort.Direction direction
)

public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
        String cursor,
        int limit,
        Sort.Direction direction
)

public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
        Instant cursorUpdatedAt,
        ID cursorId,
        int limit,
        Sort.Direction direction
)
```

Behavior:

- `findAllByUpdatedAtCursor(limit, direction)` reads the first page.
- `findAllByUpdatedAtCursor(cursor, limit, direction)` decodes the opaque cursor string inside the library.
- `findAllByUpdatedAtCursor(cursorUpdatedAt, cursorId, limit, direction)` is the lower-level typed helper.
- Sort by `updated_at`, then `id`, both in the selected direction.
- The cursor must include both `updatedAt` and `id`.
- For soft-delete entities, also apply `deleted = false`.
- Fetch `limit + 1`.

Conceptual SQL for descending order:

```sql
select *
from <table>
where (
        :cursorUpdatedAt is null
        or updated_at < :cursorUpdatedAt
        or (updated_at = :cursorUpdatedAt and <id_column> < :cursorId)
      )
  and deleted = false
order by updated_at desc, <id_column> desc
limit :limitPlusOne
```

Conceptual SQL for ascending order:

```sql
select *
from <table>
where (
        :cursorUpdatedAt is null
        or updated_at > :cursorUpdatedAt
        or (updated_at = :cursorUpdatedAt and <id_column> > :cursorId)
      )
  and deleted = false
order by updated_at asc, <id_column> asc
limit :limitPlusOne
```

The `deleted = false` predicate only applies to soft-delete entities.

#### Generic Cursor Helpers

The DAO should start with the two built-in cursor types:

- id cursor
- `updated_at + id` cursor

Do not implement arbitrary multi-column cursor pagination in v1. It requires generic row-value extraction, type-safe cursor encoding, and dynamic predicate generation for all sort combinations.

Keep the API extensible by isolating cursor logic in helper classes, for example:

```text
CursorCodec
CursorIdCodec
CursorPage
CursorDirection
IdCursor
UpdatedAtIdCursor
```

#### Streaming Methods

Add streaming methods separately from cursor pagination:

```java
public Flux<E> streamAll()
public Flux<E> streamAllByCriteria(Criteria criteria, Sort sort)
```

Behavior:

- Return `Flux<E>` directly from `R2dbcEntityTemplate.select(...)`.
- Do not collect results.
- For soft-delete entities, apply `deleted = false`.
- Endpoints using these methods should explicitly choose a streaming media type:
  - `application/x-ndjson` for newline-delimited JSON
  - `text/event-stream` for Server-Sent Events

Do not expose streaming methods as a substitute for normal API pagination. They are for export, long-running read streams, SSE, NDJSON, or internal reactive pipelines.

### Raw SQL Page Helper

Expose:

```java
public <T> Mono<Page<T>> findPage(
        String baseQuery,
        String countQuery,
        Map<String, Object> parameters,
        Pageable pageable,
        BiFunction<Row, RowMetadata, T> mapper
)
```

This method is for custom DTO projections.

Important limits:

- The DAO service cannot safely inject soft-delete filters into arbitrary SQL.
- Callers must include `deleted = false` themselves if the query targets soft-delete tables.
- This must be documented in Javadocs.

Sort safety:

- Do not blindly trust external sort properties.
- The library helper can append `ORDER BY` from `Pageable`, but consumers should normalize or whitelist sort properties before passing the pageable.
- A future improvement can add a sort mapping strategy.

Parameter binding:

- Support null parameters using `bindNull`.
- Avoid assuming all null parameters are strings if possible.
- If type-safe null binding is not implemented in v1, document that null parameters are bound as `String.class`.

## Delete Behavior

The DAO service determines delete mode by entity class:

```java
boolean softDelete = SoftDeleteEntity.class.isAssignableFrom(entityClass);
```

### Delete Entity

Expose:

```java
public Mono<Long> delete(E entity)
```

Behavior:

- Hard-delete entity: perform physical delete and return affected row count if possible.
- Soft-delete entity: update `deleted`, `deletedAt`, and `updatedAt` without fetching.

For hard delete, `ReactiveCrudRepository.delete(entity)` returns `Mono<Void>`, not row count. Since the DAO API wants row count, prefer metadata-based delete through `DatabaseClient` or `R2dbcEntityTemplate` instead of delegating to `repository.delete(entity)`.

### Delete By Id

Expose:

```java
public Mono<Long> deleteById(ID id)
```

No `deleteByIdRequired` in v1.

Behavior for hard-delete entities:

```sql
delete from <table> where <id_column> = :id
```

Behavior for soft-delete entities:

```sql
update <table>
set deleted = true,
    deleted_at = :now,
    updated_at = :now
where <id_column> = :id
  and deleted = false
```

Return the affected row count.

### Delete All

Expose:

```java
public Mono<Long> deleteAll()
```

Behavior for hard-delete entities:

```sql
delete from <table>
```

Behavior for soft-delete entities:

```sql
update <table>
set deleted = true,
    deleted_at = :now,
    updated_at = :now
where deleted = false
```

This has the same table-wide risk profile as Spring Data's `deleteAll()`. The library will not add extra safety confirmation in v1.

### Bulk Delete By Ids

Expose:

```java
public Mono<Long> deleteAllByIds(Collection<ID> ids)
```

Behavior for hard-delete entities:

```sql
delete from <table> where <id_column> in (:ids)
```

Behavior for soft-delete entities:

```sql
update <table>
set deleted = true,
    deleted_at = :now,
    updated_at = :now
where <id_column> in (:ids)
  and deleted = false
```

Return affected row count.

## Metadata Access

DAO services need table and column names.

Use Spring Data relational metadata from the R2DBC converter/mapping context rather than hard-coded table names.

Required metadata:

- Table name for `entityClass`.
- Id column name.
- Column name for `deleted`.
- Column name for `deletedAt`.
- Column name for `updatedAt`.

Expected API direction:

```java
RelationalPersistentEntity<?> entity =
        template.getConverter()
                .getMappingContext()
                .getRequiredPersistentEntity(entityClass);

SqlIdentifier tableName = entity.getTableName();
RelationalPersistentProperty idProperty = entity.getRequiredIdProperty();
SqlIdentifier idColumn = idProperty.getColumnName();
```

Verify exact Spring Data R2DBC 4.0 APIs during implementation.

For fixed soft-delete fields, resolve properties by Java property name:

```java
entity.getPersistentProperty("deleted")
entity.getPersistentProperty("deletedAt")
entity.getPersistentProperty("updatedAt")
```

Fail fast at service construction if a soft-delete entity is missing required fields.

## Exception Handling

Provide a configurable exception factory:

```java
@FunctionalInterface
public interface EntityNotFoundExceptionFactory {
    RuntimeException create(Class<?> entityClass, Object id);
}
```

Default exception:

```java
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(Class<?> entityClass, Object id) {
        super(entityClass.getSimpleName() + " not found for id: " + id);
    }
}
```

Default factory:

```java
public final class DefaultEntityNotFoundExceptionFactory
        implements EntityNotFoundExceptionFactory {
    @Override
    public RuntimeException create(Class<?> entityClass, Object id) {
        return new EntityNotFoundException(entityClass, id);
    }
}
```

Consumers can provide their own factory if they want domain-specific exceptions.

## Javadocs To Include

Add Javadocs on DAO methods that clarify:

- `save(entity, true)` forces insert with an assigned id.
- Soft-delete read filtering applies only to DAO service methods.
- User repository derived methods and `@Query` methods must handle soft-delete filtering themselves.
- Raw SQL page helpers do not rewrite SQL.
- Cursor pagination fetches `limit + 1` rows and returns an opaque `nextCursor`.
- `Flux` streaming methods are separate from cursor pagination and require an appropriate HTTP media type to stream to clients.
- Delete methods return affected row count.
- `deleteAll()` is table-wide, matching Spring Data semantics.

## Implementation Order

1. Replace generated sample `org.example` classes with real package structure.
2. Add entity classes:
   - `BaseEntity`
   - `UuidEntity`
   - `SoftDeleteEntity`
   - `SoftDeleteUuidEntity`
3. Add repository marker interfaces:
   - `SimplifiedR2dbcRepository`
   - `SimplifiedUuidR2dbcRepository`
   - optional soft-delete UUID marker
4. Add exception types and factory.
5. Implement metadata resolver/helper.
6. Implement `AbstractDaoService`.
7. Implement `AbstractUuidDaoService`.
8. Add tests with minimal test entities:
   - hard-delete UUID entity
   - soft-delete UUID entity
9. Test lifecycle:
   - save creates id
   - save sets timestamps
   - update changes `updatedAt`
   - assigned-id insert works
10. Test hard delete:
    - `deleteById` physically removes row
    - `deleteAll` physically removes rows
11. Test soft delete:
    - `deleteById` updates flags without fetching
    - `findById` does not return deleted row
    - `existsById` ignores deleted row
    - `count` ignores deleted row
    - `findAll` ignores deleted row
12. Test `findByIdRequired` default exception.
13. Test configurable exception factory.
14. Test criteria paging for hard-delete and soft-delete entities.
15. Test id cursor pagination:
    - first page
    - next page
    - ascending order
    - descending order
    - soft-deleted rows excluded
16. Test `updated_at + id` cursor pagination:
    - deterministic order when rows share the same `updatedAt`
    - next cursor includes both values
    - ascending order
    - descending order
    - soft-deleted rows excluded
17. Test streaming methods return `Flux` without collecting rows in the DAO.

## Out Of Scope For V1

- Custom repository base class overriding Spring Data repository methods.
- Rewriting derived queries.
- Rewriting `@Query` SQL.
- Soft-delete filtering for user-defined repository methods.
- Configurable clock.
- Sort property whitelisting or mapping.
- Arbitrary multi-column cursor pagination.
- Multi-column ids.
- Non-standard soft-delete property names.
- Auditing integrations.
- Optimistic locking.

## Design Rationale

The custom repository approach gives nicer call sites but requires extending Spring Data internals and carefully overriding a large method surface. It also cannot change standard delete method return types.

The DAO-service approach is more explicit:

- It owns the API.
- It can return `Mono<Long>` for delete counts.
- It can expose `save(entity, asNewWithId)`.
- It can implement soft-delete filtering predictably for its own reads.
- It is close to the proven source design from `trovanauto-v2`.

The cost is that consumers inject DAO services for generic persistence operations instead of using repositories directly for everything. That is acceptable for v1.
