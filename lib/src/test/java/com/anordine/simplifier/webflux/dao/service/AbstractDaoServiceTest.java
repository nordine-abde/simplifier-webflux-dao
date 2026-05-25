package com.anordine.simplifier.webflux.dao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anordine.simplifier.webflux.dao.cursor.CursorCodec;
import com.anordine.simplifier.webflux.dao.cursor.UpdatedAtIdCursor;
import com.anordine.simplifier.webflux.dao.entity.BaseEntity;
import com.anordine.simplifier.webflux.dao.entity.SoftDeleteUuidEntity;
import com.anordine.simplifier.webflux.dao.entity.UuidEntity;
import com.anordine.simplifier.webflux.dao.exception.EntityNotFoundException;
import com.anordine.simplifier.webflux.dao.repository.SimplifiedR2dbcRepository;
import com.anordine.simplifier.webflux.dao.repository.SimplifiedUuidR2dbcRepository;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AbstractDaoServiceTest {

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Test
    void saveInsertsHardDeleteAndSoftDeleteFixtureEntities() {
        TestContext context = createContext();
        HardDeleteFixture hard = new HardDeleteFixture();
        hard.setName("hard");
        SoftDeleteFixture soft = new SoftDeleteFixture();
        soft.setName("soft");

        StepVerifier.create(context.hardService().save(hard))
                .assertNext(saved -> {
                    assertNotNull(saved.getId());
                    assertNotNull(saved.getCreatedAt());
                    assertNotNull(saved.getUpdatedAt());
                    assertFalse(saved.isNew());
                })
                .verifyComplete();

        StepVerifier.create(context.softService().save(soft))
                .assertNext(saved -> {
                    assertNotNull(saved.getId());
                    assertNotNull(saved.getCreatedAt());
                    assertNotNull(saved.getUpdatedAt());
                    assertFalse(saved.isNew());
                    assertFalse(saved.isDeleted());
                })
                .verifyComplete();
    }

    @Test
    void saveUpdatesExistingEntity() {
        TestContext context = createContext();
        HardDeleteFixture entity = new HardDeleteFixture();
        entity.setName("before");

        HardDeleteFixture saved = context.hardService().save(entity).block();
        assertNotNull(saved);
        Instant createdAt = saved.getCreatedAt();
        Instant firstUpdatedAt = saved.getUpdatedAt();
        saved.setName("after");

        StepVerifier.create(context.hardService().save(saved))
                .assertNext(updated -> {
                    assertEquals(saved.getId(), updated.getId());
                    assertEquals("after", updated.getName());
                    assertEquals(createdAt, updated.getCreatedAt());
                    assertTrue(!updated.getUpdatedAt().isBefore(firstUpdatedAt));
                    assertFalse(updated.isNew());
                })
                .verifyComplete();
    }

    @Test
    void saveAllPreparesAndPersistsAllEntities() {
        TestContext context = createContext();
        HardDeleteFixture first = new HardDeleteFixture();
        first.setName("first");
        HardDeleteFixture second = new HardDeleteFixture();
        second.setName("second");

        StepVerifier.create(context.hardService().saveAll(List.of(first, second)).collectList())
                .assertNext(saved -> {
                    assertEquals(2, saved.size());
                    assertTrue(saved.stream().allMatch(entity -> entity.getId() != null));
                    assertTrue(saved.stream().noneMatch(BaseEntity::isNew));
                })
                .verifyComplete();

        StepVerifier.create(context.hardService().count())
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void findByIdReadsHardDeleteRows() {
        TestContext context = createContext();
        HardDeleteFixture entity = new HardDeleteFixture();
        entity.setName("visible");
        HardDeleteFixture saved = context.hardService().save(entity).block();
        assertNotNull(saved);

        StepVerifier.create(context.hardService().findById(saved.getId()))
                .assertNext(found -> {
                    assertEquals(saved.getId(), found.getId());
                    assertEquals("visible", found.getName());
                })
                .verifyComplete();
    }

    @Test
    void findByIdIgnoresSoftDeletedRows() {
        TestContext context = createContext();
        SoftDeleteFixture entity = new SoftDeleteFixture();
        entity.setName("hidden");
        SoftDeleteFixture saved = context.softService().save(entity).block();
        assertNotNull(saved);
        markSoftDeleted(context.client(), saved.getId());

        StepVerifier.create(context.softService().findById(saved.getId()))
                .verifyComplete();
    }

    @Test
    void findByIdRequiredThrowsDefaultException() {
        TestContext context = createContext();
        UUID missingId = UUID.randomUUID();

        StepVerifier.create(context.hardService().findByIdRequired(missingId))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof EntityNotFoundException);
                    assertTrue(error.getMessage().contains(HardDeleteFixture.class.getSimpleName()));
                    assertTrue(error.getMessage().contains(missingId.toString()));
                })
                .verify();
    }

    @Test
    void findByIdRequiredThrowsCustomException() {
        TestContext context = createContext();
        UUID missingId = UUID.randomUUID();
        HardDeleteDaoService service = new HardDeleteDaoService(
                context.hardRepository(),
                context.template(),
                (entityClass, id) -> new CustomMissingEntityException(entityClass.getSimpleName() + ":" + id)
        );

        StepVerifier.create(service.findByIdRequired(missingId))
                .expectErrorSatisfies(error -> {
                    assertTrue(error instanceof CustomMissingEntityException);
                    assertEquals(HardDeleteFixture.class.getSimpleName() + ":" + missingId, error.getMessage());
                })
                .verify();
    }

    @Test
    void softDeleteReadsIgnoreSoftDeletedRows() {
        TestContext context = createContext();
        SoftDeleteFixture first = softFixture("first");
        SoftDeleteFixture second = softFixture("second");
        SoftDeleteFixture third = softFixture("third");
        List<SoftDeleteFixture> saved = context.softService()
                .saveAll(List.of(first, second, third))
                .collectList()
                .block();
        assertNotNull(saved);
        UUID deletedId = saved.get(1).getId();
        markSoftDeleted(context.client(), deletedId);

        StepVerifier.create(context.softService().existsById(saved.get(0).getId()))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(context.softService().existsById(deletedId))
                .expectNext(false)
                .verifyComplete();
        StepVerifier.create(context.softService().count())
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(context.softService().findAll().map(SoftDeleteFixture::getId).collectList())
                .assertNext(ids -> {
                    assertEquals(2, ids.size());
                    assertTrue(ids.contains(saved.get(0).getId()));
                    assertFalse(ids.contains(deletedId));
                    assertTrue(ids.contains(saved.get(2).getId()));
                })
                .verifyComplete();
        StepVerifier.create(context.softService()
                        .findAllByIds(saved.stream().map(SoftDeleteFixture::getId).toList())
                        .map(SoftDeleteFixture::getId)
                        .collectList())
                .assertNext(ids -> {
                    assertEquals(2, ids.size());
                    assertTrue(ids.contains(saved.get(0).getId()));
                    assertFalse(ids.contains(deletedId));
                    assertTrue(ids.contains(saved.get(2).getId()));
                })
                .verifyComplete();
    }

    @Test
    void hardDeleteReadsDelegateToRepository() {
        TestContext context = createContext();
        HardDeleteFixture first = hardFixture("first");
        HardDeleteFixture second = hardFixture("second");
        List<HardDeleteFixture> saved = context.hardService()
                .saveAll(List.of(first, second))
                .collectList()
                .block();
        assertNotNull(saved);

        StepVerifier.create(context.hardService().existsById(saved.get(0).getId()))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(context.hardService().count())
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(context.hardService()
                        .findAllByIds(saved.stream().map(HardDeleteFixture::getId).toList())
                        .map(HardDeleteFixture::getId)
                        .collectList())
                .assertNext(ids -> {
                    assertEquals(2, ids.size());
                    assertTrue(ids.contains(saved.get(0).getId()));
                    assertTrue(ids.contains(saved.get(1).getId()));
                })
                .verifyComplete();
    }

    @Test
    void hardDeleteByIdPhysicallyRemovesOneRowAndReturnsCount() {
        TestContext context = createContext();
        HardDeleteFixture saved = context.hardService().save(hardFixture("remove")).block();
        assertNotNull(saved);

        StepVerifier.create(context.hardService().deleteById(saved.getId()))
                .expectNext(1L)
                .verifyComplete();
        StepVerifier.create(context.hardService().deleteById(saved.getId()))
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(context.template().exists(
                        Query.query(Criteria.where("id").is(saved.getId())),
                        HardDeleteFixture.class
                ))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void hardDeleteEntityDeletesByEntityId() {
        TestContext context = createContext();
        HardDeleteFixture saved = context.hardService().save(hardFixture("entity")).block();
        assertNotNull(saved);

        StepVerifier.create(context.hardService().delete(saved))
                .expectNext(1L)
                .verifyComplete();
        StepVerifier.create(context.hardService().count())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void hardDeleteAllByIdsRemovesMatchingRows() {
        TestContext context = createContext();
        List<HardDeleteFixture> saved = context.hardService()
                .saveAll(List.of(hardFixture("first"), hardFixture("second"), hardFixture("third")))
                .collectList()
                .block();
        assertNotNull(saved);

        StepVerifier.create(context.hardService().deleteAllByIds(List.of(
                        saved.get(0).getId(),
                        saved.get(2).getId(),
                        UUID.randomUUID()
                )))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(context.hardService().findAll().map(HardDeleteFixture::getId).collectList())
                .assertNext(ids -> {
                    assertEquals(1, ids.size());
                    assertEquals(saved.get(1).getId(), ids.getFirst());
                })
                .verifyComplete();
    }

    @Test
    void hardDeleteAllRemovesAllRows() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("first"), hardFixture("second")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService().deleteAll())
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(context.hardService().count())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void deleteAllByIdsWithEmptyCollectionReturnsZero() {
        TestContext context = createContext();

        StepVerifier.create(context.hardService().deleteAllByIds(List.of()))
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(context.softService().deleteAllByIds(List.of()))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void softDeleteByIdUpdatesFlagsAndTimestampsWithoutPriorFetch() {
        TestContext context = createContext();
        SoftDeleteFixture saved = context.softService().save(softFixture("remove")).block();
        assertNotNull(saved);
        Instant previousUpdatedAt = saved.getUpdatedAt();

        StepVerifier.create(context.softService().deleteById(saved.getId()))
                .expectNext(1L)
                .verifyComplete();
        StepVerifier.create(context.softService().deleteById(saved.getId()))
                .expectNext(0L)
                .verifyComplete();

        SoftDeleteFixture rawRow = context.template()
                .selectOne(Query.query(Criteria.where("id").is(saved.getId())), SoftDeleteFixture.class)
                .block();
        assertNotNull(rawRow);
        assertTrue(rawRow.isDeleted());
        assertNotNull(rawRow.getDeletedAt());
        assertTrue(!rawRow.getUpdatedAt().isBefore(previousUpdatedAt));

        StepVerifier.create(context.softService().findById(saved.getId()))
                .verifyComplete();
        StepVerifier.create(context.softService().count())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void softDeleteAllByIdsDoesNotDoubleCountRowsAlreadyDeleted() {
        TestContext context = createContext();
        List<SoftDeleteFixture> saved = context.softService()
                .saveAll(List.of(softFixture("first"), softFixture("second"), softFixture("third")))
                .collectList()
                .block();
        assertNotNull(saved);
        markSoftDeleted(context.client(), saved.get(1).getId());

        StepVerifier.create(context.softService().deleteAllByIds(
                        saved.stream().map(SoftDeleteFixture::getId).toList()
                ))
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(context.softService().deleteAllByIds(
                        saved.stream().map(SoftDeleteFixture::getId).toList()
                ))
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(context.template().count(Query.empty(), SoftDeleteFixture.class))
                .expectNext(3L)
                .verifyComplete();
        StepVerifier.create(context.softService().count())
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void softDeleteAllOnlyCountsVisibleRowsAndKeepsPhysicalRows() {
        TestContext context = createContext();
        List<SoftDeleteFixture> saved = context.softService()
                .saveAll(List.of(softFixture("first"), softFixture("second"), softFixture("third")))
                .collectList()
                .block();
        assertNotNull(saved);
        markSoftDeleted(context.client(), saved.get(0).getId());

        StepVerifier.create(context.softService().deleteAll())
                .expectNext(2L)
                .verifyComplete();
        StepVerifier.create(context.softService().deleteAll())
                .expectNext(0L)
                .verifyComplete();
        StepVerifier.create(context.template().count(Query.empty(), SoftDeleteFixture.class))
                .expectNext(3L)
                .verifyComplete();
        StepVerifier.create(context.softService().findAll())
                .verifyComplete();
    }

    @Test
    void criteriaFiltersHardDeleteRows() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("alpha"), hardFixture("beta"), hardFixture("apex")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService()
                        .findAllByCriteria(
                                Criteria.where("name").like("a%"),
                                PageRequest.of(0, 10, Sort.by("name").ascending())
                        ))
                .assertNext(page -> {
                    assertEquals(2L, page.getTotalElements());
                    assertEquals(List.of("alpha", "apex"), names(page.getContent()));
                })
                .verifyComplete();
    }

    @Test
    void criteriaFiltersSoftDeleteRowsAndExcludesDeletedRows() {
        TestContext context = createContext();
        List<SoftDeleteFixture> saved = context.softService()
                .saveAll(List.of(softFixture("alpha"), softFixture("beta"), softFixture("apex")))
                .collectList()
                .block();
        assertNotNull(saved);
        markSoftDeleted(context.client(), saved.get(2).getId());

        StepVerifier.create(context.softService()
                        .findAllByCriteria(
                                Criteria.where("name").like("a%"),
                                PageRequest.of(0, 10, Sort.by("name").ascending())
                        ))
                .assertNext(page -> {
                    assertEquals(1L, page.getTotalElements());
                    assertEquals(List.of("alpha"), names(page.getContent()));
                })
                .verifyComplete();
    }

    @Test
    void pageableContentAndTotalCountAreCorrect() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("bravo"), hardFixture("alpha"), hardFixture("charlie")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService().findAll(
                        PageRequest.of(1, 1, Sort.by("name").ascending())
                ))
                .assertNext(page -> {
                    assertEquals(3L, page.getTotalElements());
                    assertEquals(3, page.getTotalPages());
                    assertEquals(1, page.getNumber());
                    assertEquals(1, page.getNumberOfElements());
                    assertEquals(List.of("bravo"), names(page.getContent()));
                })
                .verifyComplete();
    }

    @Test
    void sortingIsHonoredForSortAndPageMethods() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("bravo"), hardFixture("alpha"), hardFixture("charlie")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService()
                        .findAll(Sort.by("name").descending())
                        .map(HardDeleteFixture::getName)
                        .collectList())
                .expectNext(List.of("charlie", "bravo", "alpha"))
                .verifyComplete();

        StepVerifier.create(context.hardService().findAllByCriteria(
                        Criteria.empty(),
                        PageRequest.of(0, 2, Sort.by("name").descending())
                ))
                .assertNext(page -> {
                    assertEquals(3L, page.getTotalElements());
                    assertEquals(List.of("charlie", "bravo"), names(page.getContent()));
                })
                .verifyComplete();
    }

    @Test
    void streamingReturnsAllHardDeleteRows() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("alpha"), hardFixture("beta")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService().streamAll().map(HardDeleteFixture::getName))
                .recordWith(ArrayList::new)
                .expectNextCount(2)
                .consumeRecordedWith(names -> {
                    assertTrue(names.contains("alpha"));
                    assertTrue(names.contains("beta"));
                })
                .verifyComplete();
    }

    @Test
    void streamingExcludesSoftDeletedRows() {
        TestContext context = createContext();
        context.softService()
                .saveAll(List.of(softFixture(uuid(1), "visible"), softFixture(uuid(2), "deleted")), true)
                .collectList()
                .block();
        markSoftDeleted(context.client(), uuid(2));

        StepVerifier.create(context.softService().streamAll().map(SoftDeleteFixture::getName))
                .expectNext("visible")
                .verifyComplete();
    }

    @Test
    void streamingCriteriaAndSortAreHonored() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture("alpha"), hardFixture("beta"), hardFixture("apex")))
                .collectList()
                .block();

        StepVerifier.create(context.hardService()
                        .streamAllByCriteria(
                                Criteria.where("name").like("a%"),
                                Sort.by("name").descending()
                        )
                        .map(HardDeleteFixture::getName))
                .expectNext("apex", "alpha")
                .verifyComplete();
    }

    @Test
    void streamingCriteriaCombinesSoftDeleteFilter() {
        TestContext context = createContext();
        context.softService()
                .saveAll(List.of(
                        softFixture(uuid(1), "alpha"),
                        softFixture(uuid(2), "apex"),
                        softFixture(uuid(3), "beta")
                ), true)
                .collectList()
                .block();
        markSoftDeleted(context.client(), uuid(2));

        StepVerifier.create(context.softService()
                        .streamAllByCriteria(
                                Criteria.where("name").like("a%"),
                                Sort.by("name").ascending()
                        )
                        .map(SoftDeleteFixture::getName))
                .expectNext("alpha")
                .verifyComplete();
    }

    @Test
    void rawSqlPageMapsDtoRowsAndReturnsTotalCount() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "alpha"),
                        hardFixture(uuid(2), "beta"),
                        hardFixture(uuid(3), "apex")
                ), true)
                .collectList()
                .block();

        StepVerifier.create(context.hardService().findPage(
                        """
                                SELECT "id" AS "id", "name" AS "name"
                                FROM "hard_delete_fixture"
                                WHERE "name" LIKE :namePrefix
                                ORDER BY "name" ASC
                                """,
                        """
                                SELECT COUNT(*)
                                FROM "hard_delete_fixture"
                                WHERE "name" LIKE :namePrefix
                                """,
                        Map.of("namePrefix", "a%"),
                        PageRequest.of(1, 1),
                        (row, metadata) -> new NameProjection(
                                row.get("id", UUID.class),
                                row.get("name", String.class)
                        )
                ))
                .assertNext(page -> {
                    assertEquals(2L, page.getTotalElements());
                    assertEquals(2, page.getTotalPages());
                    assertEquals(1, page.getNumber());
                    assertEquals(1, page.getNumberOfElements());
                    assertEquals(List.of(new NameProjection(uuid(3), "apex")), page.getContent());
                })
                .verifyComplete();
    }

    @Test
    void rawSqlPageBindsNullParameters() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture(uuid(1), "alpha"), hardFixture(uuid(2), "beta")), true)
                .collectList()
                .block();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("nameFilter", null);

        StepVerifier.create(context.hardService().findPage(
                        """
                                SELECT "id" AS "id", "name" AS "name"
                                FROM "hard_delete_fixture"
                                WHERE (:nameFilter IS NULL OR "name" = :nameFilter)
                                ORDER BY "name" ASC
                                """,
                        """
                                SELECT COUNT(*)
                                FROM "hard_delete_fixture"
                                WHERE (:nameFilter IS NULL OR "name" = :nameFilter)
                                """,
                        parameters,
                        PageRequest.of(0, 10),
                        (row, metadata) -> new NameProjection(
                                row.get("id", UUID.class),
                                row.get("name", String.class)
                        )
                ))
                .assertNext(page -> {
                    assertEquals(2L, page.getTotalElements());
                    assertEquals(List.of(
                            new NameProjection(uuid(1), "alpha"),
                            new NameProjection(uuid(2), "beta")
                    ), page.getContent());
                })
                .verifyComplete();
    }

    @Test
    void idCursorFirstPageAscending() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second")
                ), true)
                .collectList()
                .block();

        StepVerifier.create(context.hardService().findAllByIdCursor(2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertTrue(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(2)), ids(page.content()));
                    assertEquals(uuid(2), decodeIdCursor(page.nextCursor()));
                })
                .verifyComplete();
    }

    @Test
    void idCursorNextPageAscending() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(4), "fourth")
                ), true)
                .collectList()
                .block();

        String nextCursor = context.hardService()
                .findAllByIdCursor(2, Sort.Direction.ASC)
                .map(page -> page.nextCursor())
                .block();
        assertNotNull(nextCursor);

        StepVerifier.create(context.hardService().findAllByIdCursor(nextCursor, 2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(3), uuid(4)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void idCursorFirstPageDescending() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third")
                ), true)
                .collectList()
                .block();

        StepVerifier.create(context.hardService().findAllByIdCursor(2, Sort.Direction.DESC))
                .assertNext(page -> {
                    assertTrue(page.hasNext());
                    assertEquals(List.of(uuid(3), uuid(2)), ids(page.content()));
                    assertEquals(uuid(2), decodeIdCursor(page.nextCursor()));
                })
                .verifyComplete();
    }

    @Test
    void idCursorNextPageDescending() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(4), "fourth")
                ), true)
                .collectList()
                .block();

        String nextCursor = context.hardService()
                .findAllByIdCursor(2, Sort.Direction.DESC)
                .map(page -> page.nextCursor())
                .block();
        assertNotNull(nextCursor);

        StepVerifier.create(context.hardService().findAllByIdCursor(nextCursor, 2, Sort.Direction.DESC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(2), uuid(1)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void idCursorFinalPageHasNoNextCursor() {
        TestContext context = createContext();
        context.hardService()
                .saveAll(List.of(hardFixture(uuid(1), "first"), hardFixture(uuid(2), "second")), true)
                .collectList()
                .block();

        StepVerifier.create(context.hardService().findAllByIdCursor(2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(2)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void idCursorSoftDeleteRowsAreExcluded() {
        TestContext context = createContext();
        context.softService()
                .saveAll(List.of(
                        softFixture(uuid(1), "first"),
                        softFixture(uuid(2), "second"),
                        softFixture(uuid(3), "third")
                ), true)
                .collectList()
                .block();
        markSoftDeleted(context.client(), uuid(2));

        StepVerifier.create(context.softService().findAllByIdCursor(2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(3)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void idCursorRejectsInvalidLimit() {
        TestContext context = createContext();

        assertEquals(
                "limit must be greater than 0",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.hardService().findAllByIdCursor(0, Sort.Direction.ASC)
                ).getMessage()
        );
        assertEquals(
                "limit must be greater than 0",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.hardService().findAllByIdCursor(-1, Sort.Direction.ASC)
                ).getMessage()
        );
    }

    @Test
    void updatedAtCursorFirstPageAscending() {
        TestContext context = createContext();
        Instant firstUpdatedAt = Instant.parse("2026-05-05T10:15:30Z");
        Instant secondUpdatedAt = Instant.parse("2026-05-05T10:16:30Z");
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second")
                ), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(2), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(3), secondUpdatedAt);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(null, null, 2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertTrue(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(2)), ids(page.content()));
                    assertEquals(new UpdatedAtIdCursor<>(firstUpdatedAt, uuid(2)),
                            decodeUpdatedAtCursor(page.nextCursor()));
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorNextPageAscending() {
        TestContext context = createContext();
        Instant firstUpdatedAt = Instant.parse("2026-05-05T10:15:30Z");
        Instant secondUpdatedAt = Instant.parse("2026-05-05T10:16:30Z");
        Instant thirdUpdatedAt = Instant.parse("2026-05-05T10:17:30Z");
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(4), "fourth")
                ), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(2), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(3), secondUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(4), thirdUpdatedAt);

        String nextCursor = context.hardService()
                .findAllByUpdatedAtCursor(null, null, 2, Sort.Direction.ASC)
                .map(page -> page.nextCursor())
                .block();
        assertNotNull(nextCursor);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(nextCursor, 2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(3), uuid(4)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorFirstPageDescending() {
        TestContext context = createContext();
        Instant firstUpdatedAt = Instant.parse("2026-05-05T10:15:30Z");
        Instant secondUpdatedAt = Instant.parse("2026-05-05T10:16:30Z");
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third")
                ), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(2), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(3), secondUpdatedAt);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(null, null, 2, Sort.Direction.DESC))
                .assertNext(page -> {
                    assertTrue(page.hasNext());
                    assertEquals(List.of(uuid(3), uuid(2)), ids(page.content()));
                    assertEquals(new UpdatedAtIdCursor<>(firstUpdatedAt, uuid(2)),
                            decodeUpdatedAtCursor(page.nextCursor()));
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorNextPageDescending() {
        TestContext context = createContext();
        Instant firstUpdatedAt = Instant.parse("2026-05-05T10:15:30Z");
        Instant secondUpdatedAt = Instant.parse("2026-05-05T10:16:30Z");
        Instant thirdUpdatedAt = Instant.parse("2026-05-05T10:17:30Z");
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second"),
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(4), "fourth")
                ), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(2), firstUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(3), secondUpdatedAt);
        setHardUpdatedAt(context.client(), uuid(4), thirdUpdatedAt);

        String nextCursor = context.hardService()
                .findAllByUpdatedAtCursor(null, null, 2, Sort.Direction.DESC)
                .map(page -> page.nextCursor())
                .block();
        assertNotNull(nextCursor);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(nextCursor, 2, Sort.Direction.DESC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(2), uuid(1)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorUsesIdAsDeterministicTieBreaker() {
        TestContext context = createContext();
        Instant updatedAt = Instant.parse("2026-05-05T10:15:30Z");
        context.hardService()
                .saveAll(List.of(
                        hardFixture(uuid(3), "third"),
                        hardFixture(uuid(1), "first"),
                        hardFixture(uuid(2), "second")
                ), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), updatedAt);
        setHardUpdatedAt(context.client(), uuid(2), updatedAt);
        setHardUpdatedAt(context.client(), uuid(3), updatedAt);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(null, null, 3, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(2), uuid(3)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorFinalPageHasNoNextCursor() {
        TestContext context = createContext();
        Instant updatedAt = Instant.parse("2026-05-05T10:15:30Z");
        context.hardService()
                .saveAll(List.of(hardFixture(uuid(1), "first"), hardFixture(uuid(2), "second")), true)
                .collectList()
                .block();
        setHardUpdatedAt(context.client(), uuid(1), updatedAt);
        setHardUpdatedAt(context.client(), uuid(2), updatedAt);

        StepVerifier.create(context.hardService()
                        .findAllByUpdatedAtCursor(null, null, 2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(1), uuid(2)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorSoftDeletedRowsAreExcluded() {
        TestContext context = createContext();
        Instant firstUpdatedAt = Instant.parse("2026-05-05T10:15:30Z");
        Instant secondUpdatedAt = Instant.parse("2026-05-05T10:16:30Z");
        Instant thirdUpdatedAt = Instant.parse("2026-05-05T10:17:30Z");
        context.softService()
                .saveAll(List.of(
                        softFixture(uuid(1), "first"),
                        softFixture(uuid(2), "second"),
                        softFixture(uuid(3), "third")
                ), true)
                .collectList()
                .block();
        setSoftUpdatedAt(context.client(), uuid(1), firstUpdatedAt);
        setSoftUpdatedAt(context.client(), uuid(2), secondUpdatedAt);
        setSoftUpdatedAt(context.client(), uuid(3), thirdUpdatedAt);
        markSoftDeleted(context.client(), uuid(2));

        String nextCursor = context.softService()
                .findAllByUpdatedAtCursor(null, null, 1, Sort.Direction.ASC)
                .map(page -> {
                    assertTrue(page.hasNext());
                    assertEquals(List.of(uuid(1)), ids(page.content()));
                    return page.nextCursor();
                })
                .block();
        assertNotNull(nextCursor);

        StepVerifier.create(context.softService()
                        .findAllByUpdatedAtCursor(nextCursor, 2, Sort.Direction.ASC))
                .assertNext(page -> {
                    assertFalse(page.hasNext());
                    assertEquals(List.of(uuid(3)), ids(page.content()));
                    assertNull(page.nextCursor());
                })
                .verifyComplete();
    }

    @Test
    void updatedAtCursorRejectsInvalidLimitAndInconsistentCursor() {
        TestContext context = createContext();
        Instant updatedAt = Instant.parse("2026-05-05T10:15:30Z");

        assertEquals(
                "limit must be greater than 0",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.hardService()
                                .findAllByUpdatedAtCursor(null, null, 0, Sort.Direction.ASC)
                ).getMessage()
        );
        assertEquals(
                "cursorUpdatedAt and cursorId must both be null or both be non-null",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.hardService()
                                .findAllByUpdatedAtCursor(updatedAt, null, 1, Sort.Direction.ASC)
                ).getMessage()
        );
        assertEquals(
                "cursorUpdatedAt and cursorId must both be null or both be non-null",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> context.hardService()
                                .findAllByUpdatedAtCursor(null, uuid(1), 1, Sort.Direction.ASC)
                ).getMessage()
        );
    }

    @Test
    void daoServiceWorksWithScannedSpringDataRepositoryProxy() {
        try (AnnotationConfigApplicationContext applicationContext =
                new AnnotationConfigApplicationContext(SpringRepositoryTestConfiguration.class)) {
            DatabaseClient client = applicationContext.getBean(DatabaseClient.class);
            createSpringRepositorySchema(client);
            SpringRepositoryFixtureDaoService service =
                    applicationContext.getBean(SpringRepositoryFixtureDaoService.class);
            SpringRepositoryFixtureRepository repository =
                    applicationContext.getBean(SpringRepositoryFixtureRepository.class);

            SpringRepositoryFixture entity = new SpringRepositoryFixture();
            entity.setName("proxy-backed");

            StepVerifier.create(service.save(entity))
                    .assertNext(saved -> {
                        assertNotNull(saved.getId());
                        assertFalse(saved.isNew());
                        assertEquals("proxy-backed", saved.getName());
                    })
                    .verifyComplete();

            StepVerifier.create(repository.findById(entity.getId()))
                    .assertNext(found -> {
                        assertEquals(entity.getId(), found.getId());
                        assertEquals("proxy-backed", found.getName());
                        assertFalse(found.isNew());
                    })
                    .verifyComplete();

            StepVerifier.create(service.findAllByIdCursor(1, Sort.Direction.ASC))
                    .assertNext(page -> {
                        assertFalse(page.hasNext());
                        assertEquals(List.of(entity.getId()), ids(page.content()));
                        assertNull(page.nextCursor());
                    })
                    .verifyComplete();
        }
    }

    private static TestContext createContext() {
        int databaseId = DATABASE_SEQUENCE.incrementAndGet();
        ConnectionFactory connectionFactory = ConnectionFactories.get(
                "r2dbc:h2:mem:///dao_service_t10_" + databaseId + ";DB_CLOSE_DELAY=-1"
        );
        R2dbcEntityTemplate template = new R2dbcEntityTemplate(connectionFactory);
        DatabaseClient client = DatabaseClient.create(connectionFactory);
        createSchema(client);

        TemplateRepository<HardDeleteFixture> hardRepository =
                new TemplateRepository<>(template, HardDeleteFixture.class);
        TemplateRepository<SoftDeleteFixture> softRepository =
                new TemplateRepository<>(template, SoftDeleteFixture.class);

        return new TestContext(
                template,
                client,
                hardRepository,
                softRepository,
                new HardDeleteDaoService(hardRepository, template),
                new SoftDeleteDaoService(softRepository, template)
        );
    }

    private static void createSchema(DatabaseClient client) {
        StepVerifier.create(Flux.concat(
                        client.sql("""
                                CREATE TABLE "hard_delete_fixture" (
                                    "id" UUID PRIMARY KEY,
                                    "name" VARCHAR(255),
                                    "created_at" TIMESTAMP WITH TIME ZONE,
                                    "updated_at" TIMESTAMP WITH TIME ZONE
                                )
                                """).fetch().rowsUpdated(),
                        client.sql("""
                                CREATE TABLE "soft_delete_fixture" (
                                    "id" UUID PRIMARY KEY,
                                    "name" VARCHAR(255),
                                    "created_at" TIMESTAMP WITH TIME ZONE,
                                    "updated_at" TIMESTAMP WITH TIME ZONE,
                                    "deleted" BOOLEAN NOT NULL,
                                    "deleted_at" TIMESTAMP WITH TIME ZONE
                                )
                                """).fetch().rowsUpdated()
                ))
                .expectNextCount(2)
                .verifyComplete();
    }

    private static void createSpringRepositorySchema(DatabaseClient client) {
        StepVerifier.create(client.sql("""
                        CREATE TABLE "spring_repository_fixture" (
                            "id" UUID PRIMARY KEY,
                            "name" VARCHAR(255),
                            "created_at" TIMESTAMP WITH TIME ZONE,
                            "updated_at" TIMESTAMP WITH TIME ZONE
                        )
                        """)
                .fetch()
                .rowsUpdated())
                .expectNext(0L)
                .verifyComplete();
    }

    private static void markSoftDeleted(DatabaseClient client, UUID id) {
        StepVerifier.create(client.sql("""
                        UPDATE "soft_delete_fixture"
                        SET "deleted" = TRUE,
                            "deleted_at" = CURRENT_TIMESTAMP,
                            "updated_at" = CURRENT_TIMESTAMP
                        WHERE "id" = :id
                        """)
                .bind("id", id)
                .fetch()
                .rowsUpdated())
                .expectNext(1L)
                .verifyComplete();
    }

    private static void setHardUpdatedAt(DatabaseClient client, UUID id, Instant updatedAt) {
        setUpdatedAt(client, "hard_delete_fixture", id, updatedAt);
    }

    private static void setSoftUpdatedAt(DatabaseClient client, UUID id, Instant updatedAt) {
        setUpdatedAt(client, "soft_delete_fixture", id, updatedAt);
    }

    private static void setUpdatedAt(DatabaseClient client, String tableName, UUID id, Instant updatedAt) {
        StepVerifier.create(client.sql("""
                        UPDATE "%s"
                        SET "updated_at" = :updatedAt
                        WHERE "id" = :id
                        """.formatted(tableName))
                .bind("updatedAt", updatedAt)
                .bind("id", id)
                .fetch()
                .rowsUpdated())
                .expectNext(1L)
                .verifyComplete();
    }

    private static HardDeleteFixture hardFixture(String name) {
        HardDeleteFixture entity = new HardDeleteFixture();
        entity.setName(name);
        return entity;
    }

    private static HardDeleteFixture hardFixture(UUID id, String name) {
        HardDeleteFixture entity = hardFixture(name);
        entity.setId(id);
        return entity;
    }

    private static SoftDeleteFixture softFixture(String name) {
        SoftDeleteFixture entity = new SoftDeleteFixture();
        entity.setName(name);
        return entity;
    }

    private static SoftDeleteFixture softFixture(UUID id, String name) {
        SoftDeleteFixture entity = softFixture(name);
        entity.setId(id);
        return entity;
    }

    private static List<String> names(List<? extends NamedFixture> fixtures) {
        return fixtures.stream()
                .map(NamedFixture::getName)
                .toList();
    }

    private static List<UUID> ids(List<? extends BaseEntity<UUID>> fixtures) {
        return fixtures.stream()
                .map(BaseEntity::getId)
                .toList();
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012x", value));
    }

    private static UUID decodeIdCursor(String cursor) {
        return new CursorCodec().decodeIdCursor(cursor, UUID::fromString).id();
    }

    private static UpdatedAtIdCursor<UUID> decodeUpdatedAtCursor(String cursor) {
        return new CursorCodec().decodeUpdatedAtIdCursor(cursor, UUID::fromString);
    }

    private record TestContext(
            R2dbcEntityTemplate template,
            DatabaseClient client,
            TemplateRepository<HardDeleteFixture> hardRepository,
            TemplateRepository<SoftDeleteFixture> softRepository,
            HardDeleteDaoService hardService,
            SoftDeleteDaoService softService
    ) {
    }

    private static final class HardDeleteDaoService
            extends AbstractDaoService<HardDeleteFixture, TemplateRepository<HardDeleteFixture>, UUID> {

        private HardDeleteDaoService(
                TemplateRepository<HardDeleteFixture> repository,
                R2dbcEntityTemplate template
        ) {
            super(repository, template, HardDeleteFixture.class);
        }

        private HardDeleteDaoService(
                TemplateRepository<HardDeleteFixture> repository,
                R2dbcEntityTemplate template,
                com.anordine.simplifier.webflux.dao.exception.EntityNotFoundExceptionFactory exceptionFactory
        ) {
            super(repository, template, HardDeleteFixture.class, exceptionFactory);
        }
    }

    private static final class SoftDeleteDaoService
            extends AbstractDaoService<SoftDeleteFixture, TemplateRepository<SoftDeleteFixture>, UUID> {

        private SoftDeleteDaoService(
                TemplateRepository<SoftDeleteFixture> repository,
                R2dbcEntityTemplate template
        ) {
            super(repository, template, SoftDeleteFixture.class);
        }
    }

    private static final class SpringRepositoryFixtureDaoService
            extends AbstractUuidDaoService<SpringRepositoryFixture, SpringRepositoryFixtureRepository> {

        private SpringRepositoryFixtureDaoService(
                SpringRepositoryFixtureRepository repository,
                R2dbcEntityTemplate template
        ) {
            super(repository, template, SpringRepositoryFixture.class);
        }
    }

    interface SpringRepositoryFixtureRepository
            extends SimplifiedUuidR2dbcRepository<SpringRepositoryFixture> {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableR2dbcRepositories(
            basePackageClasses = AbstractDaoServiceTest.class,
            considerNestedRepositories = true
    )
    static class SpringRepositoryTestConfiguration {

        @Bean
        ConnectionFactory connectionFactory() {
            return ConnectionFactories.get(
                    "r2dbc:h2:mem:///dao_service_repository_proxy_"
                            + DATABASE_SEQUENCE.incrementAndGet()
                            + ";DB_CLOSE_DELAY=-1"
            );
        }

        @Bean
        R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
            return new R2dbcEntityTemplate(connectionFactory);
        }

        @Bean
        DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
            return DatabaseClient.create(connectionFactory);
        }

        @Bean
        SpringRepositoryFixtureDaoService springRepositoryFixtureDaoService(
                SpringRepositoryFixtureRepository repository,
                R2dbcEntityTemplate template
        ) {
            return new SpringRepositoryFixtureDaoService(repository, template);
        }
    }

    private static final class TemplateRepository<E extends BaseEntity<UUID>>
            implements SimplifiedR2dbcRepository<E, UUID> {

        private final R2dbcEntityTemplate template;
        private final Class<E> entityClass;

        private TemplateRepository(R2dbcEntityTemplate template, Class<E> entityClass) {
            this.template = template;
            this.entityClass = entityClass;
        }

        @Override
        public <S extends E> Mono<S> save(S entity) {
            if (entity.isNew()) {
                return template.insert(entity);
            }
            return template.update(entity);
        }

        @Override
        public <S extends E> Flux<S> saveAll(Iterable<S> entities) {
            return Flux.fromIterable(entities).concatMap(this::save);
        }

        @Override
        public <S extends E> Flux<S> saveAll(Publisher<S> entityStream) {
            return Flux.from(entityStream).concatMap(this::save);
        }

        @Override
        public Mono<E> findById(UUID id) {
            return template.selectOne(Query.query(Criteria.where("id").is(id)), entityClass);
        }

        @Override
        public Mono<E> findById(Publisher<UUID> idStream) {
            return Mono.from(idStream).flatMap(this::findById);
        }

        @Override
        public Mono<Boolean> existsById(UUID id) {
            return template.exists(Query.query(Criteria.where("id").is(id)), entityClass);
        }

        @Override
        public Mono<Boolean> existsById(Publisher<UUID> idStream) {
            return Mono.from(idStream).flatMap(this::existsById);
        }

        @Override
        public Flux<E> findAll() {
            return template.select(Query.empty(), entityClass);
        }

        @Override
        public Flux<E> findAllById(Iterable<UUID> ids) {
            List<UUID> idList = new ArrayList<>();
            ids.forEach(idList::add);
            if (idList.isEmpty()) {
                return Flux.empty();
            }
            return template.select(Query.query(Criteria.where("id").in(idList)), entityClass);
        }

        @Override
        public Flux<E> findAllById(Publisher<UUID> idStream) {
            return Flux.from(idStream).collectList().flatMapMany(this::findAllById);
        }

        @Override
        public Mono<Long> count() {
            return template.count(Query.empty(), entityClass);
        }

        @Override
        public Mono<Void> deleteById(UUID id) {
            return unsupported();
        }

        @Override
        public Mono<Void> deleteById(Publisher<UUID> idStream) {
            return unsupported();
        }

        @Override
        public Mono<Void> delete(E entity) {
            return unsupported();
        }

        @Override
        public Mono<Void> deleteAllById(Iterable<? extends UUID> ids) {
            return unsupported();
        }

        @Override
        public Mono<Void> deleteAll(Iterable<? extends E> entities) {
            return unsupported();
        }

        @Override
        public Mono<Void> deleteAll(Publisher<? extends E> entityStream) {
            return unsupported();
        }

        @Override
        public Mono<Void> deleteAll() {
            return unsupported();
        }

        private Mono<Void> unsupported() {
            return Mono.error(new UnsupportedOperationException("Delete methods are outside T06"));
        }
    }

    @Table("hard_delete_fixture")
    static class HardDeleteFixture extends UuidEntity implements NamedFixture {

        @Column("name")
        private String name;

        @Override
        public String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    @Table("soft_delete_fixture")
    static class SoftDeleteFixture extends SoftDeleteUuidEntity implements NamedFixture {

        @Column("name")
        private String name;

        @Override
        public String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    @Table("spring_repository_fixture")
    static class SpringRepositoryFixture extends UuidEntity implements NamedFixture {

        @Column("name")
        private String name;

        @Override
        public String getName() {
            return name;
        }

        void setName(String name) {
            this.name = name;
        }
    }

    private static final class CustomMissingEntityException extends RuntimeException {

        private CustomMissingEntityException(String message) {
            super(message);
        }
    }

    private interface NamedFixture {

        String getName();
    }

    private record NameProjection(UUID id, String name) {
    }
}
