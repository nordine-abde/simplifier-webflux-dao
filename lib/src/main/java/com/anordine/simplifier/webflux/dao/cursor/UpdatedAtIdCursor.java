package com.anordine.simplifier.webflux.dao.cursor;

import java.time.Instant;
import java.util.Objects;

/**
 * Cursor value for seek pagination ordered by updated-at plus id.
 *
 * @param updatedAt updated-at value for the last returned row
 * @param id id value for the last returned row
 * @param <ID> id type
 */
public record UpdatedAtIdCursor<ID>(
        Instant updatedAt,
        ID id
) {

    /**
     * Creates an updated-at plus id cursor.
     */
    public UpdatedAtIdCursor {
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(id, "id must not be null");
    }
}
