package anordine.dao.simplifier.webflux.exception;

/**
 * Generic exception thrown by DAO required-read methods when an entity row is
 * absent.
 */
public class EntityNotFoundException extends RuntimeException {

    private final Class<?> entityClass;
    private final Object id;

    public EntityNotFoundException(Class<?> entityClass, Object id) {
        super(entityClass.getSimpleName() + " not found for id: " + id);
        this.entityClass = entityClass;
        this.id = id;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public Object getId() {
        return id;
    }
}
