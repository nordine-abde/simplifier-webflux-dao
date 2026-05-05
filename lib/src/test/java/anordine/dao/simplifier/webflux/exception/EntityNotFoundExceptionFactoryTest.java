package anordine.dao.simplifier.webflux.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntityNotFoundExceptionFactoryTest {

    @Test
    void defaultExceptionMessageIncludesEntitySimpleNameAndId() {
        UUID id = UUID.fromString("0b5bf003-f042-4a0b-bf8b-821d88bc4c2f");
        DefaultEntityNotFoundExceptionFactory factory = new DefaultEntityNotFoundExceptionFactory();

        RuntimeException exception = factory.create(TestEntity.class, id);

        EntityNotFoundException notFound = assertInstanceOf(EntityNotFoundException.class, exception);
        assertEquals("TestEntity not found for id: 0b5bf003-f042-4a0b-bf8b-821d88bc4c2f",
                notFound.getMessage());
        assertSame(TestEntity.class, notFound.getEntityClass());
        assertEquals(id, notFound.getId());
    }

    @Test
    void customFactoryCanCreateCallerDefinedRuntimeException() {
        UUID id = UUID.fromString("fdc7c7d3-2f0d-4d6d-a835-1e550ad76d57");
        EntityNotFoundExceptionFactory factory = (entityClass, missingId) ->
                new TestDomainNotFoundException(entityClass.getSimpleName(), missingId);

        RuntimeException exception = factory.create(TestEntity.class, id);

        TestDomainNotFoundException domainException =
                assertInstanceOf(TestDomainNotFoundException.class, exception);
        assertEquals("Missing TestEntity with id fdc7c7d3-2f0d-4d6d-a835-1e550ad76d57",
                domainException.getMessage());
    }

    private static class TestEntity {
    }

    private static class TestDomainNotFoundException extends RuntimeException {

        TestDomainNotFoundException(String entityName, Object id) {
            super("Missing " + entityName + " with id " + id);
        }
    }
}
