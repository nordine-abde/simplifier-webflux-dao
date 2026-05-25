package com.anordine.simplifier.webflux.dao.service;

import com.anordine.simplifier.webflux.dao.cursor.CursorIdCodec;
import com.anordine.simplifier.webflux.dao.entity.UuidEntity;
import com.anordine.simplifier.webflux.dao.exception.EntityNotFoundExceptionFactory;
import com.anordine.simplifier.webflux.dao.repository.SimplifiedUuidR2dbcRepository;
import java.util.UUID;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

/**
 * Convenience DAO service base for hard-delete UUID entities.
 *
 * @param <E> entity type
 * @param <R> repository type
 */
public abstract class AbstractUuidDaoService<
        E extends UuidEntity,
        R extends SimplifiedUuidR2dbcRepository<E>>
        extends AbstractDaoService<E, R, UUID> {

    /**
     * Creates a UUID DAO service using the default not-found exception
     * factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class
     */
    protected AbstractUuidDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass
    ) {
        super(repository, template, entityClass, CursorIdCodec.uuid());
    }

    /**
     * Creates a UUID DAO service using a custom not-found exception factory.
     *
     * @param repository Spring Data repository for the entity
     * @param template R2DBC entity template
     * @param entityClass concrete entity class
     * @param exceptionFactory factory used by required-read methods
     */
    protected AbstractUuidDaoService(
            R repository,
            R2dbcEntityTemplate template,
            Class<E> entityClass,
            EntityNotFoundExceptionFactory exceptionFactory
    ) {
        super(repository, template, entityClass, exceptionFactory, CursorIdCodec.uuid());
    }
}
