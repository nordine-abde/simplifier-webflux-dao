package anordine.dao.simplifier.webflux.service;

import anordine.dao.simplifier.webflux.entity.BaseEntity;
import anordine.dao.simplifier.webflux.exception.DefaultEntityNotFoundExceptionFactory;
import anordine.dao.simplifier.webflux.exception.EntityNotFoundExceptionFactory;
import anordine.dao.simplifier.webflux.metadata.EntityMetadata;
import anordine.dao.simplifier.webflux.metadata.EntityMetadataResolver;
import anordine.dao.simplifier.webflux.repository.SimplifiedR2dbcRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    protected final R repository;
    protected final R2dbcEntityTemplate template;
    protected final Class<E> entityClass;
    protected final EntityNotFoundExceptionFactory exceptionFactory;
    protected final EntityMetadata metadata;

    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass
    ) {
        this(repository, template, entityClass, new DefaultEntityNotFoundExceptionFactory());
    }

    protected AbstractDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            EntityNotFoundExceptionFactory exceptionFactory
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.template = Objects.requireNonNull(template, "template must not be null");
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass must not be null");
        this.exceptionFactory = Objects.requireNonNull(
                exceptionFactory,
                "exceptionFactory must not be null"
        );
        this.metadata = new EntityMetadataResolver(template).resolve(entityClass);
    }

    /**
     * Saves an entity after preparing DAO-managed lifecycle fields.
     */
    @Transactional
    public Mono<E> save(E entity) {
        return save(entity, false);
    }

    /**
     * Saves an entity after preparing DAO-managed lifecycle fields.
     *
     * @param asNewWithId when {@code true}, forces an insert using an already
     * assigned id
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
     */
    @Transactional
    public Flux<E> saveAll(Collection<E> entities) {
        return saveAll(entities, false);
    }

    /**
     * Saves all entities after preparing DAO-managed lifecycle fields.
     *
     * @param asNewWithId when {@code true}, forces inserts using already
     * assigned ids
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
     */
    @Transactional(readOnly = true)
    public Flux<E> findAll(Sort sort) {
        Objects.requireNonNull(sort, "sort must not be null");
        return template.select(readQuery(Criteria.empty()).sort(sort), entityClass);
    }

    /**
     * Finds one classic count-backed page. Soft-delete entities return only
     * rows with {@code deleted = false}.
     */
    @Transactional(readOnly = true)
    public Mono<Page<E>> findAll(Pageable pageable) {
        return findAllByCriteria(Criteria.empty(), pageable);
    }

    /**
     * Finds one classic count-backed page by criteria. Soft-delete entities
     * combine the caller criteria with {@code deleted = false}.
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
     * Finds rows for the given ids. Soft-delete entities return only rows with
     * {@code deleted = false}.
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
     * Deletes a row by the entity id and returns the affected row count.
     * Soft-delete entities are updated in place instead of being physically
     * removed.
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
}
