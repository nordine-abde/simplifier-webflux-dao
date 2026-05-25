package com.anordine.simplifier.webflux.dao.example.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    @Test
    void mapsRequestToNewEntity() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserRequest request = new UserRequest(
                id,
                "user@example.com",
                "Example User",
                "ADMIN",
                "ACTIVE",
                "Rome"
        );

        UserEntity user = UserMapper.toAssignedIdEntity(request);

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Example User");
        assertThat(user.getRole()).isEqualTo("ADMIN");
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getCity()).isEqualTo("Rome");
    }

    @Test
    void normalNewEntityDoesNotCopyAssignedId() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UserRequest request = new UserRequest(
                id,
                "user@example.com",
                "Example User",
                "ADMIN",
                "ACTIVE",
                "Rome"
        );

        UserEntity user = UserMapper.toNewEntity(request);

        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isEqualTo("user@example.com");
    }
}
