package com.anordine.simplifier.webflux.dao.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPageTest {

    @Test
    void cursorPageExposesContentNextCursorAndHasNextState() {
        CursorPage<String> page = new CursorPage<>(
                List.of("first", "second"),
                "opaque-next-cursor",
                true
        );

        assertEquals(List.of("first", "second"), page.content());
        assertEquals("opaque-next-cursor", page.nextCursor());
        assertTrue(page.hasNext());
    }

    @Test
    void cursorPageDefensivelyCopiesContent() {
        List<String> content = new ArrayList<>(List.of("first"));

        CursorPage<String> page = new CursorPage<>(content, null, false);
        content.add("second");

        assertEquals(List.of("first"), page.content());
        assertThrows(UnsupportedOperationException.class, () -> page.content().add("third"));
    }
}
