package com.anordine.simplifier.webflux.dao.service;

import com.anordine.simplifier.webflux.dao.cursor.CursorIdCodec;
import com.anordine.simplifier.webflux.dao.entity.SoftDeleteUuidEntity;
import com.anordine.simplifier.webflux.dao.exception.EntityNotFoundExceptionFactory;
import com.anordine.simplifier.webflux.dao.repository.SimplifiedSoftDeleteUuidR2dbcRepository;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

/**
 * Convenience DAO service base for soft-delete UUID entities.
 *
 * @param <E> entity type
 * @param <R> repository type
 */
public abstract class AbstractSoftDeleteUuidDaoService<
        E extends SoftDeleteUuidEntity,
        R extends SimplifiedSoftDeleteUuidR2dbcRepository<E>>
        extends AbstractDaoService<E, R, UUID> {

    /**
     * Creates a soft-delete UUID DAO service using the default not-found
     * exception factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class
     */
    protected AbstractSoftDeleteUuidDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass
    ) {
        super(repository, template, entityClass, CursorIdCodec.uuid());
    }

    /**
     * Creates a soft-delete UUID DAO service using a custom not-found exception
     * factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class
     * @param exceptionFactory factory used by required-read methods
     */
    protected AbstractSoftDeleteUuidDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            EntityNotFoundExceptionFactory exceptionFactory
    ) {
        super(repository, template, entityClass, exceptionFactory, CursorIdCodec.uuid());
    }
}
