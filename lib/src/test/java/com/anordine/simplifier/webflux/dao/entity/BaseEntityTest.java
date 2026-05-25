package com.anordine.simplifier.webflux.dao.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;

class BaseEntityTest {

    @Test
    void uuidEntityGeneratesIdWhenAbsent() {
        TestUuidEntity entity = new TestUuidEntity();
        long before = System.currentTimeMillis();

        entity.prePersist();
        long after = System.currentTimeMillis();

        assertNotNull(entity.getId());
        UUID generatedId = assertInstanceOf(UUID.class, entity.getId());
        assertEquals(7, generatedId.version());
        assertEquals(2, generatedId.variant());
        assertTrue(uuidV7TimestampMillis(generatedId) >= before);
        assertTrue(uuidV7TimestampMillis(generatedId) <= after);
        assertTrue(entity.isNew());
    }

    @Test
    void uuidEntitiesProduceSequentiallyOrderedIds() {
        TestUuidEntity first = new TestUuidEntity();
        first.prePersist();
        UUID previous = first.getId();

        for (int i = 0; i < 100; i++) {
            TestUuidEntity entity = new TestUuidEntity();
            entity.prePersist();
            UUID current = entity.getId();

            assertTrue(Long.compareUnsigned(
                    previous.getMostSignificantBits(),
                    current.getMostSignificantBits()
            ) < 0);
            assertEquals(7, current.version());
            assertEquals(2, current.variant());
            previous = current;
        }
    }

    @Test
    void prePersistInitializesTimestampsForNewEntity() {
        TestUuidEntity entity = new TestUuidEntity();

        entity.prePersist();

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
        assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Test
    void prePersistRefreshesUpdatedAtWithoutResettingCreatedAtAfterInsert() throws InterruptedException {
        TestUuidEntity entity = new TestUuidEntity();
        entity.prePersist();
        Instant createdAt = entity.getCreatedAt();
        Instant firstUpdatedAt = entity.getUpdatedAt();
        entity.markAsNotNew();

        Thread.sleep(10);
        entity.prePersist();

        assertFalse(entity.isNew());
        assertEquals(createdAt, entity.getCreatedAt());
        assertNotEquals(firstUpdatedAt, entity.getUpdatedAt());
        assertTrue(entity.getUpdatedAt().isAfter(firstUpdatedAt));
    }

    @Test
    void prePersistCanForceAssignedIdInsert() {
        UUID assignedId = UUID.randomUUID();
        TestUuidEntity entity = new TestUuidEntity();
        entity.setId(assignedId);

        entity.prePersist(true);

        assertEquals(assignedId, entity.getId());
        assertTrue(entity.isNew());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void prePersistRejectsForcedAssignedIdInsertWhenIdIsMissing() {
        TestUuidEntity entity = new TestUuidEntity();

        assertThrows(IllegalArgumentException.class, () -> entity.prePersist(true));

        assertNull(entity.getId());
        assertFalse(entity.isNew());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void markAsNotNewClearsSpringDataInsertFlag() {
        TestUuidEntity entity = new TestUuidEntity();
        entity.prePersist();

        entity.markAsNotNew();

        assertFalse(entity.isNew());
    }

    @Test
    void softDeleteEntityDefaultsToVisibleRow() {
        TestSoftDeleteUuidEntity entity = new TestSoftDeleteUuidEntity();

        assertFalse(entity.isDeleted());
        assertNull(entity.getDeletedAt());

        assertDoesNotThrow(() -> entity.prePersist());
        assertNotNull(entity.getId());
    }

    @Test
    void entityColumnsUseFixedNames() throws NoSuchFieldException {
        assertColumn(BaseEntity.class, "id", "id");
        assertColumn(BaseEntity.class, "createdAt", "created_at");
        assertColumn(BaseEntity.class, "updatedAt", "updated_at");
        assertColumn(SoftDeleteEntity.class, "deleted", "deleted");
        assertColumn(SoftDeleteEntity.class, "deletedAt", "deleted_at");

        assertNotNull(BaseEntity.class.getDeclaredField("id").getAnnotation(Id.class));
        assertNotNull(BaseEntity.class.getDeclaredField("isNew").getAnnotation(Transient.class));
    }

    private static void assertColumn(Class<?> type, String fieldName, String columnName)
            throws NoSuchFieldException {
        Field field = type.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals(columnName, column.value());
    }

    private static long uuidV7TimestampMillis(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    private static class TestUuidEntity extends UuidEntity {
    }

    private static class TestSoftDeleteUuidEntity extends SoftDeleteUuidEntity {
    }
}
