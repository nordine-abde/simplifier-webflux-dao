package com.anordine.simplifier.webflux.dao.example.users;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message
) {
}
