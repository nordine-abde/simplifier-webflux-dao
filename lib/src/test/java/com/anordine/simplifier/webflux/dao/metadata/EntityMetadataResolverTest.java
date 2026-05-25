package com.anordine.simplifier.webflux.dao.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anordine.simplifier.webflux.dao.entity.SoftDeleteUuidEntity;
import com.anordine.simplifier.webflux.dao.entity.UuidEntity;
import io.r2dbc.spi.ConnectionFactories;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

class EntityMetadataResolverTest {

    private final R2dbcEntityTemplate template = new R2dbcEntityTemplate(
            ConnectionFactories.get("r2dbc:h2:mem:///metadata_resolver"));

    private final EntityMetadataResolver resolver = new EntityMetadataResolver(template);

    @Test
    void resolvesTableAndIdColumnForHardDeleteEntity() {
        EntityMetadata metadata = resolver.resolve(HardDeleteFixture.class);

        assertEquals(HardDeleteFixture.class, metadata.entityClass());
        assertEquals("hard_delete_fixture", metadata.tableName().getReference());
        assertEquals("id", metadata.idProperty().getName());
        assertEquals("id", metadata.idColumn().getReference());
        assertEquals("created_at", metadata.createdAtColumn().getReference());
        assertEquals("updated_at", metadata.updatedAtColumn().getReference());
        assertFalse(metadata.softDeleteCapable());
        assertTrue(metadata.softDeleteMetadata().isEmpty());

        assertEquals(render(metadata.tableName()), metadata.renderedTableName());
        assertEquals(render(metadata.idColumn()), metadata.renderedIdColumn());
        assertEquals(render(metadata.createdAtColumn()), metadata.renderedCreatedAtColumn());
        assertEquals(render(metadata.updatedAtColumn()), metadata.renderedUpdatedAtColumn());
    }

    @Test
    void resolvesFixedSoftDeleteColumnsForSoftDeleteEntity() {
        EntityMetadata metadata = resolver.resolve(SoftDeleteFixture.class);

        assertTrue(metadata.softDeleteCapable());
        EntityMetadata.SoftDeleteMetadata softDelete = metadata.requireSoftDeleteMetadata();
        assertEquals("deleted", softDelete.deletedColumn().getReference());
        assertEquals("deleted_at", softDelete.deletedAtColumn().getReference());
        assertEquals(render(softDelete.deletedColumn()), softDelete.renderedDeletedColumn());
        assertEquals(render(softDelete.deletedAtColumn()), softDelete.renderedDeletedAtColumn());
    }

    @Test
    void requireSoftDeleteMetadataFailsForHardDeleteEntity() {
        EntityMetadata metadata = resolver.resolve(HardDeleteFixture.class);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                metadata::requireSoftDeleteMetadata
        );

        assertTrue(exception.getMessage().contains(HardDeleteFixture.class.getName()));
        assertTrue(exception.getMessage().contains("not soft-delete capable"));
    }

    @Test
    void missingRequiredMappedPropertyFailsClearly() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> resolver.resolve(MissingTimestampFixture.class)
        );

        assertTrue(exception.getMessage().contains("createdAt"));
        assertTrue(exception.getMessage().contains(MissingTimestampFixture.class.getName()));
    }

    @Test
    void constructorRejectsNullTemplate() {
        assertThrows(NullPointerException.class, () -> new EntityMetadataResolver(null));
    }

    @Test
    void resolveRejectsNullEntityClass() {
        assertThrows(NullPointerException.class, () -> resolver.resolve(null));
    }

    private String render(org.springframework.data.relational.core.sql.SqlIdentifier identifier) {
        return template.getDataAccessStrategy().toSql(identifier);
    }

    @Table("hard_delete_fixture")
    private static class HardDeleteFixture extends UuidEntity {
    }

    @Table("soft_delete_fixture")
    private static class SoftDeleteFixture extends SoftDeleteUuidEntity {
    }

    @Table("missing_timestamp_fixture")
    private static class MissingTimestampFixture {

        @Id
        @Column("id")
        private UUID id;

        @Column("updated_at")
        private Instant updatedAt;
    }
}
