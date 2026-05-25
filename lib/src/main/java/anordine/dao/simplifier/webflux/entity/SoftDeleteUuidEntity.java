package anordine.dao.simplifier.webflux.entity;

import java.util.UUID;

/**
 * Base soft-delete entity with UUID identifiers.
 */
public abstract class SoftDeleteUuidEntity extends SoftDeleteEntity<UUID> {

    /**
     * Creates a UUID soft-delete entity.
     */
    protected SoftDeleteUuidEntity() {
    }

    /**
     * Generates a UUIDv7 identifier for new soft-delete entities.
     *
     * @return generated UUIDv7 identifier
     */
    @Override
    protected UUID generateId() {
        return UuidV7Generator.generate();
    }
}
