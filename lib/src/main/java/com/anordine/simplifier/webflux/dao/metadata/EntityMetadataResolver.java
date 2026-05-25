package com.anordine.simplifier.webflux.dao.metadata;

import com.anordine.simplifier.webflux.dao.entity.SoftDeleteEntity;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * Resolves the Spring Data relational metadata used by DAO-owned reads,
 * deletes, and generated SQL.
 */
public final class EntityMetadataResolver {

    private static final String CREATED_AT_PROPERTY = "createdAt";
    private static final String UPDATED_AT_PROPERTY = "updatedAt";
    private static final String DELETED_PROPERTY = "deleted";
    private static final String DELETED_AT_PROPERTY = "deletedAt";

    private final R2dbcEntityTemplate template;

    /**
     * Creates a resolver using the mapping context and dialect from the
     * supplied template.
     *
     * @param template R2DBC entity template
     */
    public EntityMetadataResolver(R2dbcEntityTemplate template) {
        this.template = Objects.requireNonNull(template, "template must not be null");
    }

    /**
     * Resolves required table, id, lifecycle timestamp, and optional
     * soft-delete metadata for an entity class.
     *
     * @param entityClass entity type to resolve
     * @return resolved metadata
     * @throws IllegalStateException if required mapped properties are missing
     */
    public EntityMetadata resolve(Class<?> entityClass) {
        Objects.requireNonNull(entityClass, "entityClass must not be null");

        RelationalPersistentEntity<?> entity = template.getConverter()
                .getMappingContext()
                .getRequiredPersistentEntity(entityClass);

        RelationalPersistentProperty idProperty = entity.getIdProperty();
        if (idProperty == null) {
            throw missingProperty(entityClass, "id");
        }

        RelationalPersistentProperty createdAt = requiredProperty(entity, CREATED_AT_PROPERTY);
        RelationalPersistentProperty updatedAt = requiredProperty(entity, UPDATED_AT_PROPERTY);
        EntityMetadata.SoftDeleteMetadata softDeleteMetadata = null;

        if (SoftDeleteEntity.class.isAssignableFrom(entityClass)) {
            RelationalPersistentProperty deleted = requiredProperty(entity, DELETED_PROPERTY);
            RelationalPersistentProperty deletedAt = requiredProperty(entity, DELETED_AT_PROPERTY);
            softDeleteMetadata = new EntityMetadata.SoftDeleteMetadata(
                    deleted.getColumnName(),
                    render(deleted.getColumnName()),
                    deletedAt.getColumnName(),
                    render(deletedAt.getColumnName())
            );
        }

        SqlIdentifier idColumn = idProperty.getColumnName();

        return new EntityMetadata(
                entityClass,
                entity.getTableName(),
                render(entity.getTableName()),
                idProperty,
                idColumn,
                render(idColumn),
                createdAt.getColumnName(),
                render(createdAt.getColumnName()),
                updatedAt.getColumnName(),
                render(updatedAt.getColumnName()),
                Optional.ofNullable(softDeleteMetadata)
        );
    }

    private RelationalPersistentProperty requiredProperty(
            RelationalPersistentEntity<?> entity,
            String propertyName
    ) {
        RelationalPersistentProperty property = entity.getPersistentProperty(propertyName);
        if (property == null) {
            throw missingProperty(entity.getType(), propertyName);
        }
        return property;
    }

    private IllegalStateException missingProperty(Class<?> entityClass, String propertyName) {
        return new IllegalStateException(
                "Missing required mapped property '" + propertyName + "' for entity "
                        + entityClass.getName());
    }

    private String render(SqlIdentifier identifier) {
        return template.getDataAccessStrategy().toSql(identifier);
    }
}
