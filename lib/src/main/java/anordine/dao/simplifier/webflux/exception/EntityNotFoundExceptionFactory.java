package anordine.dao.simplifier.webflux.exception;

/**
 * Creates the runtime exception used when DAO required-read methods cannot
 * find an entity by id.
 */
@FunctionalInterface
public interface EntityNotFoundExceptionFactory {

    RuntimeException create(Class<?> entityClass, Object id);
}
