package com.anordine.simplifier.webflux.dao.example.users;

import com.anordine.simplifier.webflux.dao.cursor.CursorPage;
import com.anordine.simplifier.webflux.dao.service.AbstractSoftDeleteUuidDaoService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class UserDaoService
        extends AbstractSoftDeleteUuidDaoService<UserEntity, UserRepository> {

    public UserDaoService(UserRepository repository, R2dbcEntityTemplate template) {
        super(
                repository,
                template,
                UserEntity.class,
                (entityClass, id) -> new UserNotFoundException("User not found: " + id)
        );
    }

    public Mono<UserEntity> create(UserRequest request) {
        if (request.id() != null) {
            return Mono.error(new IllegalArgumentException("id is only accepted by the import endpoint"));
        }
        return save(UserMapper.toNewEntity(request));
    }

    public Mono<UserEntity> importWithAssignedId(UserRequest request) {
        if (request.id() == null) {
            return Mono.error(new IllegalArgumentException("id is required for assigned-id import"));
        }
        return save(UserMapper.toAssignedIdEntity(request), true);
    }

    public Mono<UserEntity> update(UUID id, UserRequest request) {
        return findByIdRequired(id)
                .flatMap(user -> {
                    UserMapper.apply(user, request);
                    return save(user);
                });
    }

    public Mono<Page<UserEntity>> search(
            String emailContains,
            String status,
            String city,
            Pageable pageable
    ) {
        return findAllByCriteria(searchCriteria(emailContains, status, city), pageable);
    }

    public Flux<UserEntity> streamSearch(
            String status,
            String city,
            Sort sort
    ) {
        return streamAllByCriteria(searchCriteria(null, status, city), sort);
    }

    public Mono<CursorPage<UserEntity>> idCursor(String cursor, int limit, Sort.Direction direction) {
        return findAllByIdCursor(cursor, limit, direction);
    }

    public Mono<CursorPage<UserEntity>> updatedAtCursor(String cursor, int limit, Sort.Direction direction) {
        return findAllByUpdatedAtCursor(cursor, limit, direction);
    }

    public Mono<Page<UserSummary>> statusCounts(Pageable pageable) {
        String baseQuery = """
                SELECT status, COUNT(*) AS total
                FROM app_user
                WHERE deleted = false
                GROUP BY status
                ORDER BY status
                """;
        String countQuery = """
                SELECT COUNT(*)
                FROM (
                    SELECT status
                    FROM app_user
                    WHERE deleted = false
                    GROUP BY status
                ) grouped_status
                """;
        return findPage(
                baseQuery,
                countQuery,
                Map.of(),
                pageable,
                (row, rowMetadata) -> new UserSummary(
                        row.get("status", String.class),
                        row.get("total", Number.class).longValue()
                )
        );
    }

    private Criteria searchCriteria(String emailContains, String status, String city) {
        Criteria criteria = Criteria.empty();
        criteria = and(criteria, "email", emailContains, true);
        criteria = and(criteria, "status", status, false);
        criteria = and(criteria, "city", city, false);
        return criteria;
    }

    private Criteria and(Criteria criteria, String property, String value, boolean like) {
        if (value == null || value.isBlank()) {
            return criteria;
        }
        Criteria next = like
                ? Criteria.where(property).like("%" + value.strip() + "%")
                : Criteria.where(property).is(value.strip());
        return criteria.isEmpty() ? next : criteria.and(next);
    }
}
