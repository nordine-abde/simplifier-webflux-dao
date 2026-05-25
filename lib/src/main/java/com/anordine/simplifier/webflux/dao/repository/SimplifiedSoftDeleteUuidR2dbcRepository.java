package com.anordine.simplifier.webflux.dao.repository;

import com.anordine.simplifier.webflux.dao.entity.SoftDeleteUuidEntity;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Marker repository for soft-delete entities with UUID identifiers.
 *
 * <p>This interface adds no soft-delete behavior. Soft-delete filtering and
 * delete semantics are implemented by DAO services only.
 *
 * @param <E> entity type
 */
@NoRepositoryBean
public interface SimplifiedSoftDeleteUuidR2dbcRepository<E extends SoftDeleteUuidEntity>
        extends SimplifiedR2dbcRepository<E, UUID> {
}
