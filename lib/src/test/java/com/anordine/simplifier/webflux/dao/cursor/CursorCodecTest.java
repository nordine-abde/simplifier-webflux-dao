package com.anordine.simplifier.webflux.dao.cursor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void idCursorRoundTrips() {
        UUID id = UUID.fromString("6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416");

        String encoded = codec.encode(new IdCursor<>(id));
        IdCursor<UUID> decoded = codec.decodeIdCursor(encoded, UUID::fromString);

        assertEquals(new IdCursor<>(id), decoded);
        assertFalse(encoded.contains(id.toString()));
    }

    @Test
    void updatedAtPlusIdCursorRoundTrips() {
        Instant updatedAt = Instant.parse("2026-05-05T10:15:30Z");
        UUID id = UUID.fromString("6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416");

        String encoded = codec.encode(new UpdatedAtIdCursor<>(updatedAt, id));
        UpdatedAtIdCursor<UUID> decoded = codec.decodeUpdatedAtIdCursor(encoded, UUID::fromString);

        assertEquals(new UpdatedAtIdCursor<>(updatedAt, id), decoded);
        assertFalse(encoded.contains(updatedAt.toString()));
        assertFalse(encoded.contains(id.toString()));
    }

    @Test
    void malformedCursorFailsClearly() {
        CursorDecodingException exception = assertThrows(
                CursorDecodingException.class,
                () -> codec.decodeIdCursor("***")
        );

        assertTrue(exception.getMessage().contains("Malformed cursor"));
        assertTrue(exception.getMessage().contains("Base64-url"));
    }

    @Test
    void malformedCursorWithWrongShapeFailsClearly() {
        String encoded = base64Url("ID");

        CursorDecodingException exception = assertThrows(
                CursorDecodingException.class,
                () -> codec.decodeIdCursor(encoded)
        );

        assertEquals("Malformed cursor: expected 2 parts but found 1", exception.getMessage());
    }

    @Test
    void cursorTypeMismatchFailsClearly() {
        String updatedAtCursor = codec.encode(new UpdatedAtIdCursor<>(
                Instant.parse("2026-05-05T10:15:30Z"),
                UUID.fromString("6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416")
        ));

        CursorDecodingException exception = assertThrows(
                CursorDecodingException.class,
                () -> codec.decodeIdCursor(updatedAtCursor)
        );

        assertEquals(
                "Cursor type mismatch: expected ID but found UPDATED_AT_ID",
                exception.getMessage()
        );
    }

    @Test
    void invalidUpdatedAtValueFailsClearly() {
        String encoded = base64Url("UPDATED_AT_ID\n"
                + base64Url("not-an-instant")
                + "\n"
                + base64Url("6a35d4d2-3b0e-4cc1-b2a2-40b7c3fd0416"));

        CursorDecodingException exception = assertThrows(
                CursorDecodingException.class,
                () -> codec.decodeUpdatedAtIdCursor(encoded)
        );

        assertEquals("Malformed cursor: updatedAt is not a valid Instant", exception.getMessage());
    }

    @Test
    void invalidIdValueFailsClearly() {
        String encoded = codec.encode(new IdCursor<>("not-a-uuid"));

        CursorDecodingException exception = assertThrows(
                CursorDecodingException.class,
                () -> codec.decodeIdCursor(encoded, UUID::fromString)
        );

        assertEquals("Malformed cursor: id cannot be decoded", exception.getMessage());
    }

    @Test
    void customIdCursorUsesExplicitEncoderAndDecoder() {
        CustomId id = new CustomId("tenant", 42);

        String encoded = codec.encode(new IdCursor<>(id), customId -> customId.tenant() + ":" + customId.value());
        IdCursor<CustomId> decoded = codec.decodeIdCursor(encoded, CustomId::parse);

        assertEquals(new IdCursor<>(id), decoded);
        assertFalse(encoded.contains("tenant"));
        assertFalse(encoded.contains("42"));
    }

    @Test
    void defaultEncoderRejectsCustomIdTypes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> codec.encode(new IdCursor<>(new CustomId("tenant", 42)))
        );

        assertTrue(exception.getMessage().contains("provide an explicit id encoder"));
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record CustomId(String tenant, int value) {

        private static CustomId parse(String value) {
            String[] parts = value.split(":", -1);
            return new CustomId(parts[0], Integer.parseInt(parts[1]));
        }
    }
}
