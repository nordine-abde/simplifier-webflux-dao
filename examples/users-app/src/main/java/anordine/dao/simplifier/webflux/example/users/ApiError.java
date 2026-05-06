package anordine.dao.simplifier.webflux.example.users;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message
) {
}
