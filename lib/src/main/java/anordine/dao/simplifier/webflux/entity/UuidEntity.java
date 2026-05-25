package anordine.dao.simplifier.webflux.entity;

import java.util.UUID;

/**
 * Base hard-delete entity with UUID identifiers.
 */
public abstract class UuidEntity extends BaseEntity<UUID> {

    /**
     * Creates a UUID hard-delete entity.
     */
    protected UuidEntity() {
    }

    /**
     * Generates a UUIDv7 identifier for new hard-delete entities.
     *
     * @return generated UUIDv7 identifier
     */
    @Override
    protected UUID generateId() {
        return UuidV7Generator.generate();
    }
}
