package com.anordine.simplifier.webflux.dao.entity;

import java.time.Instant;

import org.jspecify.annotations.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;

/**
 * Base entity for DAO-managed lifecycle timestamps and Spring Data R2DBC
 * insert/update detection.
 *
 * <p>The fixed mapped columns are {@code id}, {@code created_at}, and
 * {@code updated_at}. DAO services call {@link #prePersist()} or
 * {@link #prePersist(boolean)} before saving and {@link #markAsNotNew()} after
 * a successful save.
 *
 * @param <ID> entity identifier type
 */
public abstract class BaseEntity<ID> implements Persistable<@NonNull ID> {

    /**
     * Mapped entity id.
     */
    @Id
    @Column("id")
    protected ID id;

    /**
     * Mapped insert timestamp.
     */
    @Column("created_at")
    protected Instant createdAt;

    /**
     * Mapped last-update timestamp.
     */
    @Column("updated_at")
    protected Instant updatedAt;

    /**
     * Transient Spring Data insert flag.
     */
    @Transient
    protected boolean isNew;

    /**
     * Creates a base entity.
     */
    protected BaseEntity() {
    }

    /**
     * Returns the entity identifier used by Spring Data R2DBC.
     *
     * @return entity id
     */
    @Override
    public ID getId() {
        return id;
    }

    /**
     * Sets the entity identifier.
     *
     * @param id entity id
     */
    public void setId(ID id) {
        this.id = id;
    }

    /**
     * Returns the timestamp assigned when the row is first inserted.
     *
     * @return created-at timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the created-at timestamp.
     *
     * @param createdAt created-at timestamp
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the timestamp refreshed each time DAO save preparation runs.
     *
     * @return updated-at timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the updated-at timestamp.
     *
     * @param updatedAt updated-at timestamp
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns whether Spring Data should insert this entity instead of updating
     * it.
     *
     * @return whether this entity is new
     */
    @Override
    public boolean isNew() {
        return isNew;
    }

    /**
     * Prepares the entity for a normal DAO save.
     *
     * <p>If the id is absent, this method generates one and marks the entity as
     * new. It sets {@code createdAt} only for inserts and refreshes
     * {@code updatedAt} for every save preparation.
     */
    public void prePersist() {
        prePersist(false);
    }

    /**
     * Prepares the entity for a DAO save.
     *
     * <p>When {@code asNewWithId} is {@code true}, the entity must already have
     * an assigned id and is forced to insert as a new row. This supports
     * assigned-id import and seed-data use cases.
     *
     * @param asNewWithId whether to force an insert using an already assigned id
     * @throws IllegalArgumentException if {@code asNewWithId} is {@code true}
     * and the id is missing
     * @throws IllegalStateException if id generation returns {@code null}
     */
    public void prePersist(boolean asNewWithId) {
        if (asNewWithId) {
            if (id == null) {
                throw new IllegalArgumentException("Cannot force insert with an assigned id when id is null");
            }
            isNew = true;
        } else if (id == null) {
            id = generateId();
            if (id == null) {
                throw new IllegalStateException("Generated id must not be null");
            }
            isNew = true;
        }

        Instant now = Instant.now();
        if (isNew) {
            createdAt = now;
        }
        updatedAt = now;
    }

    /**
     * Clears the Spring Data insert flag after a successful DAO save.
     */
    public void markAsNotNew() {
        this.isNew = false;
    }

    /**
     * Generates an id when an entity without an id is prepared for insert.
     *
     * @return generated id, never {@code null}
     */
    protected abstract ID generateId();
}
