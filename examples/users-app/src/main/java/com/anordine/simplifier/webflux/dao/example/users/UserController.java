package com.anordine.simplifier.webflux.dao.example.users;

import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "id",
            "email",
            "displayName",
            "role",
            "status",
            "city",
            "createdAt",
            "updatedAt"
    );

    private final UserDaoService dao;
    private final UserRepository repository;

    public UserController(UserDaoService dao, UserRepository repository) {
        this.dao = dao;
        this.repository = repository;
    }

    @PostMapping
    public Mono<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return dao.create(request).map(UserResponse::fromEntity);
    }

    @PostMapping("/import")
    public Mono<UserResponse> importWithAssignedId(@Valid @RequestBody UserRequest request) {
        return dao.importWithAssignedId(request).map(UserResponse::fromEntity);
    }

    @PutMapping("/{id}")
    public Mono<UserResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UserRequest request
    ) {
        return dao.update(id, request).map(UserResponse::fromEntity);
    }

    @GetMapping("/{id}")
    public Mono<UserResponse> findById(@PathVariable UUID id) {
        return dao.findById(id).map(UserResponse::fromEntity);
    }

    @GetMapping("/{id}/required")
    public Mono<UserResponse> findByIdRequired(@PathVariable UUID id) {
        return dao.findByIdRequired(id).map(UserResponse::fromEntity);
    }

    @GetMapping
    public Flux<UserResponse> findAll(
            @RequestParam(defaultValue = "email") String sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.findAll(sort(sort, direction)).map(UserResponse::fromEntity);
    }

    @GetMapping("/ids")
    public Flux<UserResponse> findAllByIds(@RequestParam List<UUID> ids) {
        return dao.findAllByIds(ids).map(UserResponse::fromEntity);
    }

    @GetMapping("/count")
    public Mono<Long> count() {
        return dao.count();
    }

    @GetMapping("/exists/email")
    public Mono<Boolean> existsByEmail(@RequestParam String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @GetMapping("/page")
    public Mono<PageResponse<UserResponse>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "email") String sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.findAll(pageable(page, size, sort, direction))
                .map(result -> result.map(UserResponse::fromEntity))
                .map(PageResponse::fromPage);
    }

    @GetMapping("/search")
    public Mono<PageResponse<UserResponse>> search(
            @RequestParam(required = false) String emailContains,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "email") String sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.search(emailContains, status, city, pageable(page, size, sort, direction))
                .map(result -> result.map(UserResponse::fromEntity))
                .map(PageResponse::fromPage);
    }

    @GetMapping("/cursor/id")
    public Mono<CursorPageResponse<UserResponse>> idCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.idCursor(cursor, limit, direction)
                .map(page -> CursorPageResponse.fromPage(page, UserResponse::fromEntity));
    }

    @GetMapping("/cursor/id/after/{id}")
    public Mono<CursorPageResponse<UserResponse>> idCursorAfterId(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.findAllByIdCursorAfterId(id, limit, direction)
                .map(page -> CursorPageResponse.fromPage(page, UserResponse::fromEntity));
    }

    @GetMapping("/cursor/updated-at")
    public Mono<CursorPageResponse<UserResponse>> updatedAtCursor(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction
    ) {
        return dao.updatedAtCursor(cursor, limit, direction)
                .map(page -> CursorPageResponse.fromPage(page, UserResponse::fromEntity));
    }

    @GetMapping(value = "/stream.ndjson", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<UserResponse> streamNdjson(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city
    ) {
        return dao.streamSearch(status, city, Sort.by(Sort.Direction.ASC, "email"))
                .map(UserResponse::fromEntity);
    }

    @GetMapping(value = "/stream.sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<UserResponse>> streamSse(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city
    ) {
        return dao.streamSearch(status, city, Sort.by(Sort.Direction.ASC, "email"))
                .delayElements(Duration.ofMillis(150))
                .map(user -> ServerSentEvent.builder(UserResponse.fromEntity(user))
                        .event("user")
                        .id(user.getId().toString())
                        .build());
    }

    @GetMapping("/reports/status-counts")
    public Mono<PageResponse<UserSummary>> statusCounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return dao.statusCounts(PageRequest.of(page, boundedSize(size)))
                .map(PageResponse::fromPage);
    }

    @GetMapping("/repository/email/{email}")
    public Mono<UserResponse> repositoryFindByEmail(@PathVariable String email) {
        return repository.findByEmailIgnoreCase(email).map(UserResponse::fromEntity);
    }

    @GetMapping("/repository/city/{city}")
    public Flux<UserResponse> repositoryFindByCity(@PathVariable String city) {
        return repository.findByCityIgnoreCaseOrderByEmailAsc(city).map(UserResponse::fromEntity);
    }

    @GetMapping("/repository/status/{status}")
    public Flux<UserResponse> repositoryFindByStatus(@PathVariable String status) {
        return repository.findByStatusOrderByUpdatedAtDesc(status).map(UserResponse::fromEntity);
    }

    @GetMapping("/repository/status/{status}/count")
    public Mono<Long> repositoryCountByStatus(@PathVariable String status) {
        return repository.countByStatus(status);
    }

    @DeleteMapping("/{id}")
    public Mono<DeleteResponse> deleteById(@PathVariable UUID id) {
        return dao.deleteById(id).map(DeleteResponse::new);
    }

    @DeleteMapping
    public Mono<DeleteResponse> deleteByIds(@RequestParam List<UUID> ids) {
        return dao.deleteAllByIds(ids).map(DeleteResponse::new);
    }

    @DeleteMapping("/all")
    public Mono<DeleteResponse> deleteAllVisible() {
        return dao.deleteAll().map(DeleteResponse::new);
    }

    private Pageable pageable(int page, int size, String sort, Sort.Direction direction) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        return PageRequest.of(page, boundedSize(size), sort(sort, direction));
    }

    private Sort sort(String property, Sort.Direction direction) {
        if (!SORT_PROPERTIES.contains(property)) {
            throw new IllegalArgumentException("Unsupported sort property: " + property);
        }
        return Sort.by(direction, property);
    }

    private int boundedSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        return Math.min(size, 100);
    }
}
