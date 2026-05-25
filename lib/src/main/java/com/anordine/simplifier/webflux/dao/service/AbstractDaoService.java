package com.anordine.simplifier.webflux.dao.service;

import com.anordine.simplifier.webflux.dao.entity.BaseEntity;
import com.anordine.simplifier.webflux.dao.cursor.CursorCodec;
import com.anordine.simplifier.webflux.dao.cursor.CursorDecodingException;
import com.anordine.simplifier.webflux.dao.cursor.CursorIdCodec;
import com.anordine.simplifier.webflux.dao.cursor.CursorPage;
import com.anordine.simplifier.webflux.dao.cursor.IdCursor;
import com.anordine.simplifier.webflux.dao.cursor.UpdatedAtIdCursor;
import com.anordine.simplifier.webflux.dao.exception.DefaultEntityNotFoundExceptionFactory;
import com.anordine.simplifier.webflux.dao.exception.EntityNotFoundExceptionFactory;
import com.anordine.simplifier.webflux.dao.metadata.EntityMetadata;
import com.anordine.simplifier.webflux.dao.metadata.EntityMetadataResolver;
import com.anordine.simplifier.webflux.dao.repository.SimplifiedR2dbcRepository;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.convert.EntityRowMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Base DAO service for lifecycle-aware saves and DAO-owned reads.
 *
 * <p>Soft-delete filtering applies only to methods implemented by this service.
 * Application repository methods, derived queries, and {@code @Query} methods
 * remain application responsibility.
 *
 * @param <E> entity type
 * @param <R> repository type
 * @param <ID> entity identifier type
 */
public abstract class AbstractDaoService<
        E extends BaseEntity<ID>,
        R extends SimplifiedR2dbcRepository<E, ID>,
        ID> {

    private static final String RAW_PAGE_LIMIT_PARAMETER = "__daoPageLimit";
    private static final String RAW_PAGE_OFFSET_PARAMETER = "__daoPageOffset";

    /**
     * Spring Data repository used for thin repository operations.
     */
    protected final R repository;

    /**
     * R2DBC template used for DAO-owned queries and SQL.
     */
    protected final R2dbcEntityTemplate template;

    /**
     * Concrete entity class supplied by the subclass.
     */
    protected final Class<E> entityClass;

    /**
     * Factory used by required-read methods.
     */
    protected final EntityNotFoundExceptionFactory exceptionFactory;

    /**
     * Resolved Spring Data relational metadata for this DAO entity.
     */
    protected final EntityMetadata metadata;
    private final CursorCodec cursorCodec = new CursorCodec();
    private final CursorIdCodec<ID> cursorIdCodec;

    /**
     * Creates a DAO service using the default not-found exception factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class; passed explicitly to avoid
     * fragile generic reflection
     */
    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass
    ) {
        this(repository, template, entityClass, new DefaultEntityNotFoundExceptionFactory(), null);
    }

    /**
     * Creates a DAO service using the default not-found exception factory and
     * an explicit cursor id codec.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class; passed explicitly to avoid
     * fragile generic reflection
     * @param cursorIdCodec codec used for opaque cursor id values
     */
    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            CursorIdCodec<ID> cursorIdCodec
    ) {
        this(
                repository,
                template,
                entityClass,
                new DefaultEntityNotFoundExceptionFactory(),
                cursorIdCodec
        );
    }

    /**
     * Creates a DAO service using a custom not-found exception factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class; passed explicitly to avoid
     * fragile generic reflection
     * @param exceptionFactory factory used by required-read methods
     */
    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            EntityNotFoundExceptionFactory exceptionFactory
    ) {
        this(repository, template, entityClass, exceptionFactory, null);
    }

    /**
     * Creates a DAO service using a custom not-found exception factory and an
     * explicit cursor id codec.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class; passed explicitly to avoid
     * fragile generic reflection
     * @param exceptionFactory factory used by required-read methods
     * @param cursorIdCodec codec used for opaque cursor id values
     */
    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            EntityNotFoundExceptionFactory exceptionFactory,
            CursorIdCodec<ID> cursorIdCodec
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.exceptionFactory = Objects.requireNonNull(
                exceptionFactory,
                "exceptionFactory must not be null"
        );
        this.metadata = new EntityMetadataResolver(template).resolve(entityClass);
        this.cursorIdCodec = cursorIdCodec != null ? cursorIdCodec : defaultCursorIdCodec();
    }

    /**
     * Saves an entity after preparing DAO-managed lifecycle fields.
     *
     * @param entity entity to save
     * @return saved entity
     */
    @Transactional
    public Mono<E> save(E entity) {
        return save(entity, false);
    }

    /**
     * Saves an entity after preparing DAO-managed lifecycle fields.
     *
     * @param entity entity to save
     * @param asNewWithId when {@code true}, forces an insert using an already
     * assigned id
     * @return saved entity
     */
    @Transactional
    public Mono<E> save(E entity, boolean asNewWithId) {
        Objects.requireNonNull(entity, "entity must not be null");
        entity.prePersist(asNewWithId);
        return repository.save(entity)
                .doOnNext(BaseEntity::markAsNotNew);
    }

    /**
     * Saves all entities after preparing DAO-managed lifecycle fields.
     *
     * @param entities entities to save
     * @return saved entities
     */
    @Transactional
    public Flux<E> saveAll(Collection<E> entities) {
        return saveAll(entities, false);
    }

    /**
     * Saves all entities after preparing DAO-managed lifecycle fields.
     *
     * @param entities entities to save
     * @param asNewWithId when {@code true}, forces inserts using already
     * assigned ids
     * @return saved entities
     */
    @Transactional
    public Flux<E> saveAll(Collection<E> entities, boolean asNewWithId) {
        Objects.requireNonNull(entities, "entities must not be null");
        entities.forEach(entity -> {
            Objects.requireNonNull(entity, "entities must not contain null elements");
            entity.prePersist(asNewWithId);
        });
        return repository.saveAll(entities)
                .doOnNext(BaseEntity::markAsNotNew);
    }

    /**
     * Finds a row by id. Soft-delete entities are filtered by
     * {@code deleted = false}.
     *
     * @param id entity id
     * @return matching visible entity or an empty {@link Mono}
     */
    @Transactional(readOnly = true)
    public Mono<E> findById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!metadata.softDeleteCapable()) {
            return repository.findById(id);
        }
        return template.selectOne(Query.query(visibleByIdCriteria(id)), entityClass);
    }

    /**
     * Finds a row by id or fails through the configured exception factory.
     *
     * @param id entity id
     * @return matching visible entity or an error from the exception factory
     */
    @Transactional(readOnly = true)
    public Mono<E> findByIdRequired(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        return findById(id)
                .switchIfEmpty(Mono.defer(() -> Mono.error(
                        exceptionFactory.create(entityClass, id))));
    }

    /**
     * Checks row existence by id. Soft-delete entities are filtered by
     * {@code deleted = false}.
     *
     * @param id entity id
     * @return whether a visible row exists
     */
    @Transactional(readOnly = true)
    public Mono<Boolean> existsById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!metadata.softDeleteCapable()) {
            return repository.existsById(id);
        }
        return template.exists(Query.query(visibleByIdCriteria(id)), entityClass);
    }

    /**
     * Counts rows. Soft-delete entities count only rows with
     * {@code deleted = false}.
     *
     * @return visible row count
     */
    @Transactional(readOnly = true)
    public Mono<Long> count() {
        if (!metadata.softDeleteCapable()) {
            return repository.count();
        }
        return template.count(Query.query(visibleCriteria()), entityClass);
    }

    /**
     * Finds all rows. Soft-delete entities return only rows with
     * {@code deleted = false}.
     *
     * @return visible rows
     */
    @Transactional(readOnly = true)
    public Flux<E> findAll() {
        if (!metadata.softDeleteCapable()) {
            return repository.findAll();
        }
        return template.select(Query.query(visibleCriteria()), entityClass);
    }

    /**
     * Finds all rows with the supplied sort. Soft-delete entities return only
     * rows with {@code deleted = false}.
     *
     * @param sort sort to apply
     * @return sorted visible rows
     */
    @Transactional(readOnly = true)
    public Flux<E> findAll(Sort sort) {
        Objects.requireNonNull(sort, "sort must not be null");
        return template.select(readQuery(Criteria.empty()).sort(sort), entityClass);
    }

    /**
     * Finds one classic count-backed page. Soft-delete entities return only
     * rows with {@code deleted = false}.
     *
     * @param pageable page request
     * @return page of visible rows and total count
     */
    @Transactional(readOnly = true)
    public Mono<Page<E>> findAll(Pageable pageable) {
        return findAllByCriteria(Criteria.empty(), pageable);
    }

    /**
     * Finds one classic count-backed page by criteria. Soft-delete entities
     * combine the caller criteria with {@code deleted = false}.
     *
     * @param criteria caller criteria
     * @param pageable page request
     * @return page of visible matching rows and total count
     */
    @Transactional(readOnly = true)
    public Mono<Page<E>> findAllByCriteria(Criteria criteria, Pageable pageable) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        Criteria effectiveCriteria = readCriteria(criteria);
        Query countQuery = query(effectiveCriteria);
        Query pageQuery = query(effectiveCriteria).with(pageable);

        Mono<Long> total = template.count(countQuery, entityClass);
        Mono<List<E>> content = template.select(pageQuery, entityClass).collectList();

        return Mono.zip(content, total)
                .map(result -> new PageImpl<>(result.getT1(), pageable, result.getT2()));
    }

    /**
     * Streams all rows by returning the {@link R2dbcEntityTemplate} result
     * {@link Flux} directly. Soft-delete entities return only rows with
     * {@code deleted = false}.
     *
     * <p>HTTP streaming depends on the endpoint media type, such as
     * {@code application/x-ndjson} for newline-delimited JSON or
     * {@code text/event-stream} for Server-Sent Events.
     *
     * @return direct result stream from the R2DBC template
     */
    @Transactional(readOnly = true)
    public Flux<E> streamAll() {
        return template.select(readQuery(Criteria.empty()), entityClass);
    }

    /**
     * Streams rows matching the supplied criteria and sort by returning the
     * {@link R2dbcEntityTemplate} result {@link Flux} directly. Soft-delete
     * entities combine the caller criteria with {@code deleted = false}.
     *
     * <p>HTTP streaming depends on the endpoint media type, such as
     * {@code application/x-ndjson} for newline-delimited JSON or
     * {@code text/event-stream} for Server-Sent Events.
     *
     * @param criteria caller criteria
     * @param sort sort to apply
     * @return direct result stream from the R2DBC template
     */
    @Transactional(readOnly = true)
    public Flux<E> streamAllByCriteria(Criteria criteria, Sort sort) {
        Objects.requireNonNull(criteria, "criteria must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        return template.select(readQuery(criteria).sort(sort), entityClass);
    }

    /**
     * Finds one classic count-backed page for caller-owned SQL and maps rows to
     * DTO projections.
     *
     * <p>This helper does not rewrite SQL. It does not inject soft-delete
     * predicates into {@code baseQuery} or {@code countQuery}; callers querying
     * soft-delete tables must include {@code deleted = false} themselves.
     *
     * <p>For v1 this helper appends only {@code LIMIT} and {@code OFFSET}.
     * {@link Pageable#getSort()} is not appended because raw SQL sort
     * properties need caller-owned normalization or whitelisting. Include a
     * safe {@code ORDER BY} clause in {@code baseQuery} when deterministic
     * ordering is required.
     *
     * <p>Null parameter values are supported with {@code bindNull}. Because the
     * parameter map does not carry null value types, nulls are bound as
     * {@link String} in v1.
     *
     * @param baseQuery caller-owned SQL that returns the page content
     * @param countQuery caller-owned SQL that returns a numeric count in the
     * first column
     * @param parameters named SQL parameters
     * @param pageable page request; sort is not appended in v1
     * @param mapper row mapper for DTO projection results
     * @param <T> projection type
     * @return mapped page content and total count
     */
    @Transactional(readOnly = true)
    public <T> Mono<Page<T>> findPage(
            String baseQuery,
            String countQuery,
            Map<String, Object> parameters,
            Pageable pageable,
            BiFunction<Row, RowMetadata, T> mapper
    ) {
        Objects.requireNonNull(baseQuery, "baseQuery must not be null");
        Objects.requireNonNull(countQuery, "countQuery must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");
        Objects.requireNonNull(mapper, "mapper must not be null");
        validateRawPageParameterNames(parameters, pageable);

        DatabaseClient.GenericExecuteSpec pageSpec = template.getDatabaseClient()
                .sql(pageSql(baseQuery, pageable));
        pageSpec = bindRawParameters(pageSpec, parameters);
        if (pageable.isPaged()) {
            pageSpec = pageSpec.bind(RAW_PAGE_LIMIT_PARAMETER, pageable.getPageSize())
                    .bind(RAW_PAGE_OFFSET_PARAMETER, pageable.getOffset());
        }

        DatabaseClient.GenericExecuteSpec countSpec = template.getDatabaseClient()
                .sql(trimTrailingSemicolon(countQuery));
        countSpec = bindRawParameters(countSpec, parameters);

        Mono<List<T>> content = pageSpec.map(mapper::apply).all().collectList();
        Mono<Long> total = countSpec.map((row, rowMetadata) -> countValue(row)).one();

        return Mono.zip(content, total)
                .map(result -> new PageImpl<>(result.getT1(), pageable, result.getT2()));
    }

    /**
     * Finds rows for the given ids. Soft-delete entities return only rows with
     * {@code deleted = false}.
     *
     * @param ids ids to read
     * @return visible rows with matching ids
     */
    @Transactional(readOnly = true)
    public Flux<E> findAllByIds(Collection<ID> ids) {
        Objects.requireNonNull(ids, "ids must not be null");
        if (ids.isEmpty()) {
            return Flux.empty();
        }
        if (!metadata.softDeleteCapable()) {
            return repository.findAllById(ids);
        }
        return template.select(Query.query(visibleByIdsCriteria(ids)), entityClass);
    }

    /**
     * Finds the first bounded seek page ordered by id.
     *
     * @param limit maximum number of rows to return
     * @param direction id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByIdCursor(
            int limit,
            Sort.Direction direction
    ) {
        return findAllByIdCursorAfterId(null, limit, direction);
    }

    /**
     * Finds one bounded seek page ordered by id using the opaque cursor
     * returned by a previous {@link CursorPage#nextCursor()} value. When
     * {@code cursor} is {@code null}, this reads the first page.
     *
     * @param cursor opaque cursor string returned by the previous page, or
     * {@code null}
     * @param limit maximum number of rows to return
     * @param direction id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByIdCursor(
            String cursor,
            int limit,
            Sort.Direction direction
    ) {
        ID cursorId = cursor == null
                ? null
                : cursorCodec.decodeIdCursor(cursor, cursorIdCodec::decode).id();
        return findAllByIdCursorAfterId(cursorId, limit, direction);
    }

    /**
     * Finds one bounded seek page ordered by id. When {@code cursorId} is
     * {@code null}, this reads the first page. Soft-delete entities return
     * only rows with {@code deleted = false}. The DAO fetches
     * {@code limit + 1} rows internally and returns an opaque next cursor only
     * when another page exists.
     *
     * @param cursorId last id from the previous page, or {@code null}
     * @param limit maximum number of rows to return
     * @param direction id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByIdCursorAfterId(
            ID cursorId,
            int limit,
            Sort.Direction direction
    ) {
        validateCursorLimit(limit);
        Objects.requireNonNull(direction, "direction must not be null");

        Query cursorQuery = query(readCriteria(idCursorCriteria(cursorId, direction)))
                .sort(Sort.by(direction, idPropertyName()))
                .limit(limit + 1);

        return template.select(cursorQuery, entityClass)
                .collectList()
                .map(fetchedRows -> idCursorPage(fetchedRows, limit));
    }

    /**
     * Finds the first bounded seek page ordered by updated-at and id.
     *
     * @param limit maximum number of rows to return
     * @param direction updated-at and id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
            int limit,
            Sort.Direction direction
    ) {
        return findAllByUpdatedAtCursor(null, null, limit, direction);
    }

    /**
     * Finds one bounded seek page ordered by updated-at and id using the opaque
     * cursor returned by a previous {@link CursorPage#nextCursor()} value.
     * When {@code cursor} is {@code null}, this reads the first page.
     *
     * @param cursor opaque cursor string returned by the previous page, or
     * {@code null}
     * @param limit maximum number of rows to return
     * @param direction updated-at and id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
            String cursor,
            int limit,
            Sort.Direction direction
    ) {
        UpdatedAtIdCursor<ID> decodedCursor = cursor == null
                ? null
                : cursorCodec.decodeUpdatedAtIdCursor(cursor, cursorIdCodec::decode);
        return decodedCursor == null
                ? findAllByUpdatedAtCursor(null, null, limit, direction)
                : findAllByUpdatedAtCursor(
                        decodedCursor.updatedAt(),
                        decodedCursor.id(),
                        limit,
                        direction
                );
    }

    /**
     * Finds one bounded seek page ordered by updated-at and id. When both
     * cursor values are {@code null}, this reads the first page. Soft-delete
     * entities return only rows with {@code deleted = false}. The DAO fetches
     * {@code limit + 1} rows internally and returns an opaque next cursor only
     * when another page exists.
     *
     * @param cursorUpdatedAt updated-at value from the previous page cursor, or
     * {@code null}
     * @param cursorId id value from the previous page cursor, or {@code null}
     * @param limit maximum number of rows to return
     * @param direction updated-at and id sort direction
     * @return bounded cursor page
     */
    @Transactional(readOnly = true)
    public Mono<CursorPage<E>> findAllByUpdatedAtCursor(
            Instant cursorUpdatedAt,
            ID cursorId,
            int limit,
            Sort.Direction direction
    ) {
        validateCursorLimit(limit);
        validateUpdatedAtCursor(cursorUpdatedAt, cursorId);
        Objects.requireNonNull(direction, "direction must not be null");

        DatabaseClient.GenericExecuteSpec spec = updatedAtCursorSpec(
                cursorUpdatedAt,
                cursorId,
                limit,
                direction
        );

        return spec.map(new EntityRowMapper<>(entityClass, template.getConverter()))
                .all()
                .collectList()
                .map(fetchedRows -> updatedAtCursorPage(fetchedRows, limit));
    }

    /**
     * Deletes a row by the entity id and returns the affected row count.
     * Soft-delete entities are updated in place instead of being physically
     * removed.
     *
     * @param entity entity whose id should be deleted
     * @return affected row count
     */
    @Transactional
    public Mono<Long> delete(E entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        ID id = Objects.requireNonNull(entity.getId(), "entity id must not be null");
        return deleteById(id);
    }

    /**
     * Deletes a row by id and returns the affected row count. Soft-delete
     * entities are updated directly without fetching the entity first.
     *
     * @param id entity id
     * @return affected row count
     */
    @Transactional
    public Mono<Long> deleteById(ID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (metadata.softDeleteCapable()) {
            return softDeleteByIds(List.of(id));
        }
        return hardDeleteById(id);
    }

    /**
     * Deletes all rows visible to the DAO and returns the affected row count.
     * For soft-delete entities, rows already marked deleted are not counted.
     * This is a table-wide operation, matching Spring Data {@code deleteAll()}
     * semantics.
     *
     * @return affected row count
     */
    @Transactional
    public Mono<Long> deleteAll() {
        if (metadata.softDeleteCapable()) {
            return softDeleteAll();
        }
        String sql = "DELETE FROM " + metadata.renderedTableName();
        return template.getDatabaseClient()
                .sql(sql)
                .fetch()
                .rowsUpdated();
    }

    /**
     * Deletes rows for the given ids and returns the affected row count. Empty
     * collections complete with {@code 0} without issuing SQL.
     *
     * @param ids ids to delete
     * @return affected row count
     */
    @Transactional
    public Mono<Long> deleteAllByIds(Collection<ID> ids) {
        Objects.requireNonNull(ids, "ids must not be null");
        if (ids.isEmpty()) {
            return Mono.just(0L);
        }
        List<ID> idList = ids.stream()
                .map(id -> Objects.requireNonNull(id, "ids must not contain null elements"))
                .toList();
        if (metadata.softDeleteCapable()) {
            return softDeleteByIds(idList);
        }
        return hardDeleteByIds(idList);
    }

    private Mono<Long> hardDeleteById(ID id) {
        String sql = "DELETE FROM " + metadata.renderedTableName()
                + " WHERE " + metadata.renderedIdColumn() + " = :id";
        return template.getDatabaseClient()
                .sql(sql)
                .bind("id", id)
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> hardDeleteByIds(Collection<ID> ids) {
        String sql = "DELETE FROM " + metadata.renderedTableName()
                + " WHERE " + metadata.renderedIdColumn() + " IN " + namedParameterList("id", ids.size());
        DatabaseClient.GenericExecuteSpec spec = template.getDatabaseClient().sql(sql);
        spec = bindIndexed(spec, "id", ids);
        return spec.fetch().rowsUpdated();
    }

    private Mono<Long> softDeleteAll() {
        Instant now = Instant.now();
        EntityMetadata.SoftDeleteMetadata softDelete = metadata.requireSoftDeleteMetadata();
        String sql = "UPDATE " + metadata.renderedTableName()
                + " SET " + softDelete.renderedDeletedColumn() + " = :deleted,"
                + " " + softDelete.renderedDeletedAtColumn() + " = :deletedAt,"
                + " " + metadata.renderedUpdatedAtColumn() + " = :updatedAt"
                + " WHERE " + softDelete.renderedDeletedColumn() + " = :visible";
        return template.getDatabaseClient()
                .sql(sql)
                .bind("deleted", true)
                .bind("deletedAt", now)
                .bind("updatedAt", now)
                .bind("visible", false)
                .fetch()
                .rowsUpdated();
    }

    private Mono<Long> softDeleteByIds(Collection<ID> ids) {
        Instant now = Instant.now();
        EntityMetadata.SoftDeleteMetadata softDelete = metadata.requireSoftDeleteMetadata();
        String sql = "UPDATE " + metadata.renderedTableName()
                + " SET " + softDelete.renderedDeletedColumn() + " = :deleted,"
                + " " + softDelete.renderedDeletedAtColumn() + " = :deletedAt,"
                + " " + metadata.renderedUpdatedAtColumn() + " = :updatedAt"
                + " WHERE " + metadata.renderedIdColumn() + " IN " + namedParameterList("id", ids.size())
                + " AND " + softDelete.renderedDeletedColumn() + " = :visible";
        DatabaseClient.GenericExecuteSpec spec = template.getDatabaseClient()
                .sql(sql)
                .bind("deleted", true)
                .bind("deletedAt", now)
                .bind("updatedAt", now)
                .bind("visible", false);
        spec = bindIndexed(spec, "id", ids);
        return spec.fetch().rowsUpdated();
    }

    private CursorPage<E> idCursorPage(List<E> fetchedRows, int limit) {
        boolean hasNext = fetchedRows.size() > limit;
        List<E> content = hasNext ? fetchedRows.subList(0, limit) : fetchedRows;
        String nextCursor = hasNext
                ? cursorCodec.encode(new IdCursor<>(content.getLast().getId()), cursorIdCodec::encode)
                : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }

    private CursorPage<E> updatedAtCursorPage(List<E> fetchedRows, int limit) {
        boolean hasNext = fetchedRows.size() > limit;
        List<E> content = hasNext ? fetchedRows.subList(0, limit) : fetchedRows;
        String nextCursor = hasNext
                ? cursorCodec.encode(new UpdatedAtIdCursor<>(
                        content.getLast().getUpdatedAt(),
                        content.getLast().getId()
                ), cursorIdCodec::encode)
                : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }

    private Criteria idCursorCriteria(ID cursorId, Sort.Direction direction) {
        if (cursorId == null) {
            return Criteria.empty();
        }
        if (direction.isAscending()) {
            return Criteria.where(idPropertyName()).greaterThan(cursorId);
        }
        return Criteria.where(idPropertyName()).lessThan(cursorId);
    }

    private DatabaseClient.GenericExecuteSpec updatedAtCursorSpec(
            Instant cursorUpdatedAt,
            ID cursorId,
            int limit,
            Sort.Direction direction
    ) {
        String comparison = direction.isAscending() ? ">" : "<";
        String sql = "SELECT * FROM " + metadata.renderedTableName()
                + updatedAtCursorWhereClause(cursorUpdatedAt, comparison)
                + " ORDER BY " + metadata.renderedUpdatedAtColumn() + " " + direction.name()
                + ", " + metadata.renderedIdColumn() + " " + direction.name()
                + " LIMIT :limit";
        DatabaseClient.GenericExecuteSpec spec = template.getDatabaseClient()
                .sql(sql)
                .bind("limit", limit + 1);
        if (cursorUpdatedAt != null) {
            spec = spec.bind("cursorUpdatedAt", cursorUpdatedAt)
                    .bind("cursorId", cursorId);
        }
        if (metadata.softDeleteCapable()) {
            spec = spec.bind("visible", false);
        }
        return spec;
    }

    private String updatedAtCursorWhereClause(Instant cursorUpdatedAt, String comparison) {
        List<String> predicates = new ArrayList<>();
        if (cursorUpdatedAt != null) {
            predicates.add("(" + metadata.renderedUpdatedAtColumn() + " " + comparison + " :cursorUpdatedAt"
                    + " OR (" + metadata.renderedUpdatedAtColumn() + " = :cursorUpdatedAt"
                    + " AND " + metadata.renderedIdColumn() + " " + comparison + " :cursorId))");
        }
        if (metadata.softDeleteCapable()) {
            predicates.add(metadata.requireSoftDeleteMetadata().renderedDeletedColumn() + " = :visible");
        }
        if (predicates.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", predicates);
    }

    private void validateCursorLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        if (limit == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("limit must be less than Integer.MAX_VALUE");
        }
    }

    private void validateUpdatedAtCursor(Instant cursorUpdatedAt, ID cursorId) {
        if ((cursorUpdatedAt == null) != (cursorId == null)) {
            throw new IllegalArgumentException(
                    "cursorUpdatedAt and cursorId must both be null or both be non-null"
            );
        }
    }

    private String namedParameterList(String prefix, int size) {
        StringJoiner parameters = new StringJoiner(", ", "(", ")");
        for (int index = 0; index < size; index++) {
            parameters.add(":" + prefix + index);
        }
        return parameters.toString();
    }

    private DatabaseClient.GenericExecuteSpec bindIndexed(
            DatabaseClient.GenericExecuteSpec spec,
            String prefix,
            Collection<ID> values
    ) {
        List<ID> valueList = new ArrayList<>(values);
        DatabaseClient.GenericExecuteSpec boundSpec = spec;
        for (int index = 0; index < valueList.size(); index++) {
            boundSpec = boundSpec.bind(prefix + index, valueList.get(index));
        }
        return boundSpec;
    }

    private String pageSql(String baseQuery, Pageable pageable) {
        String sql = trimTrailingSemicolon(baseQuery);
        if (pageable.isUnpaged()) {
            return sql;
        }
        return sql + " LIMIT :" + RAW_PAGE_LIMIT_PARAMETER
                + " OFFSET :" + RAW_PAGE_OFFSET_PARAMETER;
    }

    private String trimTrailingSemicolon(String sql) {
        String trimmed = sql.stripTrailing();
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1).stripTrailing();
        }
        return trimmed;
    }

    private void validateRawPageParameterNames(Map<String, Object> parameters, Pageable pageable) {
        for (String name : parameters.keySet()) {
            Objects.requireNonNull(name, "parameters must not contain null names");
        }
        if (pageable.isPaged()
                && (parameters.containsKey(RAW_PAGE_LIMIT_PARAMETER)
                        || parameters.containsKey(RAW_PAGE_OFFSET_PARAMETER))) {
            throw new IllegalArgumentException(
                    "parameters must not contain reserved raw page names "
                            + RAW_PAGE_LIMIT_PARAMETER + " or " + RAW_PAGE_OFFSET_PARAMETER
            );
        }
    }

    private DatabaseClient.GenericExecuteSpec bindRawParameters(
            DatabaseClient.GenericExecuteSpec spec,
            Map<String, Object> parameters
    ) {
        DatabaseClient.GenericExecuteSpec boundSpec = spec;
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            if (parameter.getValue() == null) {
                boundSpec = boundSpec.bindNull(parameter.getKey(), String.class);
            } else {
                boundSpec = boundSpec.bind(parameter.getKey(), parameter.getValue());
            }
        }
        return boundSpec;
    }

    private Long countValue(Row row) {
        Object value = row.get(0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("countQuery must return a numeric value in the first column");
    }

    private Query readQuery(Criteria criteria) {
        return query(readCriteria(criteria));
    }

    private Criteria readCriteria(Criteria criteria) {
        if (!metadata.softDeleteCapable()) {
            return criteria;
        }
        if (criteria.isEmpty()) {
            return visibleCriteria();
        }
        return criteria.and(visibleCriteria());
    }

    private Query query(Criteria criteria) {
        if (criteria.isEmpty()) {
            return Query.empty();
        }
        return Query.query(criteria);
    }

    private Criteria visibleByIdCriteria(ID id) {
        return Criteria.where(idPropertyName()).is(id)
                .and(deletedPropertyName()).is(false);
    }

    private Criteria visibleByIdsCriteria(Collection<ID> ids) {
        return Criteria.where(idPropertyName()).in(ids)
                .and(deletedPropertyName()).is(false);
    }

    private Criteria visibleCriteria() {
        return Criteria.where(deletedPropertyName()).is(false);
    }

    private String idPropertyName() {
        return metadata.idProperty().getName();
    }

    private String deletedPropertyName() {
        return metadata.requireSoftDeleteMetadata()
                .deletedColumn()
                .getReference();
    }

    @SuppressWarnings("unchecked")
    private CursorIdCodec<ID> defaultCursorIdCodec() {
        Class<?> idType = metadata.idProperty().getActualType();
        if (UUID.class.equals(idType)) {
            return (CursorIdCodec<ID>) CursorIdCodec.uuid();
        }
        if (String.class.equals(idType)) {
            return (CursorIdCodec<ID>) CursorIdCodec.string();
        }
        if (Long.class.equals(idType) || Long.TYPE.equals(idType)) {
            return (CursorIdCodec<ID>) CursorIdCodec.longId();
        }
        if (Integer.class.equals(idType) || Integer.TYPE.equals(idType)) {
            return (CursorIdCodec<ID>) CursorIdCodec.integerId();
        }
        return CursorIdCodec.of(
                id -> {
                    throw unsupportedCursorIdCodec(idType);
                },
                value -> {
                    throw unsupportedCursorIdCodec(idType);
                }
        );
    }

    private CursorDecodingException unsupportedCursorIdCodec(Class<?> idType) {
        return new CursorDecodingException(
                "No default cursor id codec for " + idType.getName()
                        + "; pass a CursorIdCodec to the DAO service constructor"
        );
    }
}
