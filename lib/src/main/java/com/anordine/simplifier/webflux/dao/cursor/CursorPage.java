package com.anordine.simplifier.webflux.dao.cursor;

import java.util.List;
import java.util.Objects;

/**
 * Bounded cursor page result.
 *
 * @param content returned page content
 * @param nextCursor opaque cursor for the next page, or {@code null}
 * @param hasNext whether another page is available
 * @param <T> content type
 */
public record CursorPage<T>(
        List<T> content,
        String nextCursor,
        boolean hasNext
) {

    /**
     * Creates a cursor page and defensively copies the content list.
     */
    public CursorPage {
        Objects.requireNonNull(content, "content must not be null");
        content = List.copyOf(content);
    }
}
