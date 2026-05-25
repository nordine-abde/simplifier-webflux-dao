package com.anordine.simplifier.webflux.dao.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anordine.simplifier.webflux.dao.entity.SoftDeleteUuidEntity;
import com.anordine.simplifier.webflux.dao.entity.UuidEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

class SimplifiedR2dbcRepositoryTest {

    @Test
    void repositoryMarkersAreReactiveCrudRepositories() {
        assertTrue(ReactiveCrudRepository.class.isAssignableFrom(SimplifiedR2dbcRepository.class));
        assertTrue(ReactiveCrudRepository.class.isAssignableFrom(SimplifiedUuidR2dbcRepository.class));
        assertTrue(ReactiveCrudRepository.class.isAssignableFrom(SimplifiedSoftDeleteUuidR2dbcRepository.class));
    }

    @Test
    void repositoryMarkersDeclareNoCustomBehavior() {
        assertEquals(0, SimplifiedR2dbcRepository.class.getDeclaredMethods().length);
        assertEquals(0, SimplifiedUuidR2dbcRepository.class.getDeclaredMethods().length);
        assertEquals(0, SimplifiedSoftDeleteUuidR2dbcRepository.class.getDeclaredMethods().length);
    }

    @Test
    void repositoryMarkersAreNotConcreteSpringDataRepositories() {
        assertTrue(SimplifiedR2dbcRepository.class.isAnnotationPresent(NoRepositoryBean.class));
        assertTrue(SimplifiedUuidR2dbcRepository.class.isAnnotationPresent(NoRepositoryBean.class));
        assertTrue(SimplifiedSoftDeleteUuidR2dbcRepository.class.isAnnotationPresent(NoRepositoryBean.class));
    }

    @Test
    void fixtureRepositoriesCompileAgainstMarkerInterfaces() {
        assertReactiveRepository(HardDeleteRepository.class);
        assertReactiveRepository(UuidRepository.class);
        assertReactiveRepository(SoftDeleteUuidRepository.class);
    }

    private static void assertReactiveRepository(Class<?> repositoryType) {
        assertTrue(ReactiveCrudRepository.class.isAssignableFrom(repositoryType));
    }

    private interface HardDeleteRepository
            extends SimplifiedR2dbcRepository<TestUuidEntity, UUID> {
    }

    private interface UuidRepository
            extends SimplifiedUuidR2dbcRepository<TestUuidEntity> {
    }

    private interface SoftDeleteUuidRepository
            extends SimplifiedSoftDeleteUuidR2dbcRepository<TestSoftDeleteUuidEntity> {
    }

    private static class TestUuidEntity extends UuidEntity {
    }

    private static class TestSoftDeleteUuidEntity extends SoftDeleteUuidEntity {
    }
}
