package com.anordine.simplifier.webflux.dao.metadata;

import java.util.Objects;
import java.util.Optional;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * Spring Data relational mapping metadata needed by DAO-owned SQL helpers.
 *
 * @param entityClass entity type
 * @param tableName mapped table identifier
 * @param renderedTableName table identifier rendered through the configured dialect
 * @param idProperty mapped id property
 * @param idColumn mapped id column identifier
 * @param renderedIdColumn id column rendered through the configured dialect
 * @param createdAtColumn mapped created-at column identifier
 * @param renderedCreatedAtColumn created-at column rendered through the configured dialect
 * @param updatedAtColumn mapped updated-at column identifier
 * @param renderedUpdatedAtColumn updated-at column rendered through the configured dialect
 * @param softDeleteMetadata soft-delete metadata for soft-delete entities
 */
public record EntityMetadata(
        Class<?> entityClass,
        SqlIdentifier tableName,
        String renderedTableName,
        RelationalPersistentProperty idProperty,
        SqlIdentifier idColumn,
        String renderedIdColumn,
        SqlIdentifier createdAtColumn,
        String renderedCreatedAtColumn,
        SqlIdentifier updatedAtColumn,
        String renderedUpdatedAtColumn,
        Optional<SoftDeleteMetadata> softDeleteMetadata
) {

    /**
     * Creates entity metadata.
     */
    public EntityMetadata {
        Objects.requireNonNull(entityClass, "entityClass must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(renderedTableName, "renderedTableName must not be null");
        Objects.requireNonNull(idProperty, "idProperty must not be null");
        Objects.requireNonNull(idColumn, "idColumn must not be null");
        Objects.requireNonNull(renderedIdColumn, "renderedIdColumn must not be null");
        Objects.requireNonNull(createdAtColumn, "createdAtColumn must not be null");
        Objects.requireNonNull(renderedCreatedAtColumn, "renderedCreatedAtColumn must not be null");
        Objects.requireNonNull(updatedAtColumn, "updatedAtColumn must not be null");
        Objects.requireNonNull(renderedUpdatedAtColumn, "renderedUpdatedAtColumn must not be null");
        Objects.requireNonNull(softDeleteMetadata, "softDeleteMetadata must not be null");
    }

    /**
     * Returns whether this entity has DAO-managed soft-delete metadata.
     *
     * @return whether the entity is soft-delete capable
     */
    public boolean softDeleteCapable() {
        return softDeleteMetadata.isPresent();
    }

    /**
     * Returns soft-delete metadata or fails if the entity is hard-delete only.
     *
     * @return soft-delete metadata
     * @throws IllegalStateException if this entity is not soft-delete capable
     */
    public SoftDeleteMetadata requireSoftDeleteMetadata() {
        return softDeleteMetadata.orElseThrow(() -> new IllegalStateException(
                "Entity " + entityClass.getName() + " is not soft-delete capable"));
    }

    /**
     * Spring Data relational metadata for fixed soft-delete columns.
     *
     * @param deletedColumn mapped deleted flag column identifier
     * @param renderedDeletedColumn deleted flag column rendered through the configured dialect
     * @param deletedAtColumn mapped deleted-at column identifier
     * @param renderedDeletedAtColumn deleted-at column rendered through the configured dialect
     */
    public record SoftDeleteMetadata(
            SqlIdentifier deletedColumn,
            String renderedDeletedColumn,
            SqlIdentifier deletedAtColumn,
            String renderedDeletedAtColumn
    ) {

        /**
         * Creates soft-delete metadata.
         */
        public SoftDeleteMetadata {
            Objects.requireNonNull(deletedColumn, "deletedColumn must not be null");
            Objects.requireNonNull(renderedDeletedColumn, "renderedDeletedColumn must not be null");
            Objects.requireNonNull(deletedAtColumn, "deletedAtColumn must not be null");
            Objects.requireNonNull(renderedDeletedAtColumn, "renderedDeletedAtColumn must not be null");
        }
    }
}
