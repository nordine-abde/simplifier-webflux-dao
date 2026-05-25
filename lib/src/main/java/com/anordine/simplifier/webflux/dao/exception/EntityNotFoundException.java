package com.anordine.simplifier.webflux.dao.exception;

/**
 * Generic exception thrown by DAO required-read methods when an entity row is
 * absent.
 */
public class EntityNotFoundException extends RuntimeException {

    /**
     * Missing entity type.
     */
    private final Class<?> entityClass;

    /**
     * Missing entity id.
     */
    private final Object id;

    /**
     * Creates an exception for a missing entity id.
     *
     * @param entityClass missing entity type
     * @param id missing id
     */
    public EntityNotFoundException(Class<?> entityClass, Object id) {
        super(entityClass.getSimpleName() + " not found for id: " + id);
        this.entityClass = entityClass;
        this.id = id;
    }

    /**
     * Returns the missing entity type.
     *
     * @return missing entity type
     */
    public Class<?> getEntityClass() {
        return entityClass;
    }

    /**
     * Returns the missing id.
     *
     * @return missing id
     */
    public Object getId() {
        return id;
    }
}
