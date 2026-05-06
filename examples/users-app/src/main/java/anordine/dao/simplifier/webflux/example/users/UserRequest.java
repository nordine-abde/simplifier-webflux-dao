package anordine.dao.simplifier.webflux.example.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserRequest(
        UUID id,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(max = 40) String role,
        @NotBlank @Size(max = 40) String status,
        @NotBlank @Size(max = 80) String city
) {
}
