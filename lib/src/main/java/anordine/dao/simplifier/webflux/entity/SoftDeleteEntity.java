package anordine.dao.simplifier.webflux.entity;

import java.time.Instant;
import org.springframework.data.relational.core.mapping.Column;

/**
 * Base entity for rows that are hidden by DAO-owned reads instead of being
 * physically deleted.
 *
 * <p>The fixed mapped soft-delete columns are {@code deleted} and
 * {@code deleted_at}. DAO service read methods filter these entities with
 * {@code deleted = false}; repository methods and caller-owned SQL remain the
 * application's responsibility.
 *
 * @param <ID> entity identifier type
 */
public abstract class SoftDeleteEntity<ID> extends BaseEntity<ID> {

    /**
     * Mapped soft-delete flag.
     */
    @Column("deleted")
    protected boolean deleted;

    /**
     * Mapped soft-delete timestamp.
     */
    @Column("deleted_at")
    protected Instant deletedAt;

    /**
     * Creates a soft-delete entity.
     */
    protected SoftDeleteEntity() {
    }

    /**
     * Returns whether this row has been soft deleted.
     *
     * @return whether this row has been soft deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Sets whether this row has been soft deleted.
     *
     * @param deleted whether this row has been soft deleted
     */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /**
     * Returns when this row was soft deleted, or {@code null} while visible.
     *
     * @return soft-delete timestamp, or {@code null}
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Sets when this row was soft deleted.
     *
     * @param deletedAt soft-delete timestamp
     */
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
