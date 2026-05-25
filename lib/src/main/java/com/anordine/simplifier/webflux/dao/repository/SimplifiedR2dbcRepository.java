package com.anordine.simplifier.webflux.dao.repository;

import com.anordine.simplifier.webflux.dao.entity.BaseEntity;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Marker repository for DAO-simplifier entities.
 *
 * <p>This interface intentionally adds no behavior beyond Spring Data's
 * reactive repository contract. Common persistence behavior belongs to DAO
 * services.
 *
 * @param <E> entity type
 * @param <ID> entity identifier type
 */
@NoRepositoryBean
public interface SimplifiedR2dbcRepository<E extends BaseEntity<ID>, ID>
        extends ReactiveCrudRepository<E, ID> {
}
