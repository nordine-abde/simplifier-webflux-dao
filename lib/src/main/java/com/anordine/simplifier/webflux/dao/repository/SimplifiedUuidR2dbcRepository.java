package com.anordine.simplifier.webflux.dao.repository;

import com.anordine.simplifier.webflux.dao.entity.UuidEntity;
import java.util.UUID;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Marker repository for hard-delete entities with UUID identifiers.
 *
 * <p>This interface intentionally adds no behavior beyond
 * {@link SimplifiedR2dbcRepository}.
 *
 * @param <E> entity type
 */
@NoRepositoryBean
public interface SimplifiedUuidR2dbcRepository<E extends UuidEntity>
        extends SimplifiedR2dbcRepository<E, UUID> {
}
