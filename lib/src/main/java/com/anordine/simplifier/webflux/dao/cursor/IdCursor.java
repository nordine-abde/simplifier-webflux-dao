package com.anordine.simplifier.webflux.dao.cursor;

import java.util.Objects;

/**
 * Cursor value for seek pagination ordered by id.
 *
 * @param id id value for the last returned row
 * @param <ID> id type
 */
public record IdCursor<ID>(ID id) {

    /**
     * Creates an id cursor.
     */
    public IdCursor {
        Objects.requireNonNull(id, "id must not be null");
    }
}
