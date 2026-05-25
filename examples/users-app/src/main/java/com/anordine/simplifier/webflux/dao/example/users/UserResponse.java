package com.anordine.simplifier.webflux.dao.example.users;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String role,
        String status,
        String city,
        Instant createdAt,
        Instant updatedAt,
        boolean deleted,
        Instant deletedAt
) {

    static UserResponse fromEntity(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getCity(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isDeleted(),
                user.getDeletedAt()
        );
    }
}
