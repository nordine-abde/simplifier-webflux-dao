package com.anordine.simplifier.webflux.dao.example.users;

import com.anordine.simplifier.webflux.dao.repository.SimplifiedSoftDeleteUuidR2dbcRepository;
import java.util.UUID;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserRepository extends SimplifiedSoftDeleteUuidR2dbcRepository<UserEntity> {

    Mono<UserEntity> findByEmailIgnoreCase(String email);

    Flux<UserEntity> findByCityIgnoreCaseOrderByEmailAsc(String city);

    Flux<UserEntity> findByStatusOrderByUpdatedAtDesc(String status);

    Mono<Boolean> existsByEmailIgnoreCase(String email);

    Mono<Long> countByStatus(String status);
}
