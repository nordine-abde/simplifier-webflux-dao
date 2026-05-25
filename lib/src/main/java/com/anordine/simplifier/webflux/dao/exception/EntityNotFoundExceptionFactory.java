package com.anordine.simplifier.webflux.dao.exception;

/**
 * Creates the runtime exception used when DAO required-read methods cannot
 * find an entity by id.
 */
@FunctionalInterface
public interface EntityNotFoundExceptionFactory {

    /**
     * Creates a runtime exception for a missing entity id.
     *
     * @param entityClass missing entity type
     * @param id missing id
     * @return runtime exception to emit from the required-read method
     */
    RuntimeException create(Class<?> entityClass, Object id);
}
