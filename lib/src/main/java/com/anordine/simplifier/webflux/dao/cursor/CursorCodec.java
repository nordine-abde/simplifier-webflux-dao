package com.anordine.simplifier.webflux.dao.cursor;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Encodes and decodes opaque public cursor strings.
 *
 * <p>The encoded string is Base64-url text containing a compact internal
 * payload with a stable cursor type discriminator. Callers should treat the
 * returned strings as opaque values.
 */
public final class CursorCodec {

    private static final String ID_TYPE = "ID";
    private static final String UPDATED_AT_ID_TYPE = "UPDATED_AT_ID";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    public static final String CURSOR_MUST_NOT_BE_NULL = "cursor must not be null";

    /**
     * Creates a cursor codec.
     */
    public CursorCodec() {
        //this class should probability be a static methods class FIX-ME
    }

    /**
     * Encodes an id cursor as an opaque public string.
     *
     * <p>The default encoder supports String-like, UUID, numeric, boolean,
     * enum, and Instant values. Use
     * {@link #encode(IdCursor, Function)} for custom id value types.
     *
     * @param cursor id cursor to encode
     * @return opaque Base64-url cursor string
     */
    public String encode(IdCursor<?> cursor) {
        Objects.requireNonNull(cursor, CURSOR_MUST_NOT_BE_NULL);
        return encodePayload(ID_TYPE + "\n" + encodeField(cursor.id()));
    }

    /**
     * Encodes an id cursor with an explicit id encoder.
     *
     * @param cursor id cursor to encode
     * @param idEncoder converts the id to stable cursor text
     * @param <ID> id type
     * @return opaque Base64-url cursor string
     */
    public <ID> String encode(IdCursor<ID> cursor, Function<ID, String> idEncoder) {
        Objects.requireNonNull(cursor, CURSOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(idEncoder, "idEncoder must not be null");
        return encodePayload(ID_TYPE + "\n" + encodeStringField(idEncoder.apply(cursor.id())));
    }

    /**
     * Encodes an updated-at plus id cursor as an opaque public string.
     *
     * <p>The default encoder supports String-like, UUID, numeric, boolean,
     * enum, and Instant values. Use
     * {@link #encode(UpdatedAtIdCursor, Function)} for custom id value types.
     *
     * @param cursor updated-at plus id cursor to encode
     * @return opaque Base64-url cursor string
     */
    public String encode(UpdatedAtIdCursor<?> cursor) {
        Objects.requireNonNull(cursor, CURSOR_MUST_NOT_BE_NULL);
        return encodePayload(UPDATED_AT_ID_TYPE
                + "\n" + encodeField(cursor.updatedAt())
                + "\n" + encodeField(cursor.id()));
    }

    /**
     * Encodes an updated-at plus id cursor with an explicit id encoder.
     *
     * @param cursor updated-at plus id cursor to encode
     * @param idEncoder converts the id to stable cursor text
     * @param <ID> id type
     * @return opaque Base64-url cursor string
     */
    public <ID> String encode(UpdatedAtIdCursor<ID> cursor, Function<ID, String> idEncoder) {
        Objects.requireNonNull(cursor, CURSOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(idEncoder, "idEncoder must not be null");
        return encodePayload(UPDATED_AT_ID_TYPE
                + "\n" + encodeField(cursor.updatedAt())
                + "\n" + encodeStringField(idEncoder.apply(cursor.id())));
    }

    /**
     * Decodes an opaque public string as an id cursor with a string id value.
     *
     * @param cursor opaque cursor string
     * @return decoded id cursor
     * @throws CursorDecodingException if the cursor is malformed or has the
     * wrong cursor type
     */
    public IdCursor<String> decodeIdCursor(String cursor) {
        return decodeIdCursor(cursor, Function.identity());
    }

    /**
     * Decodes an opaque public string as an id cursor.
     *
     * @param cursor opaque cursor string
     * @param idDecoder converts the encoded id text to the caller id type
     * @param <ID> id type
     * @return decoded id cursor
     * @throws CursorDecodingException if the cursor is malformed, has the wrong
     * cursor type, or the id cannot be decoded
     */
    public <ID> IdCursor<ID> decodeIdCursor(String cursor, Function<String, ID> idDecoder) {
        Objects.requireNonNull(idDecoder, "idDecoder must not be null");
        String[] parts = decodeParts(cursor, ID_TYPE, 2);
        return new IdCursor<>(decodeId(parts[1], idDecoder));
    }

    /**
     * Decodes an opaque public string as an updated-at plus id cursor with a
     * string id value.
     *
     * @param cursor opaque cursor string
     * @return decoded updated-at plus id cursor
     * @throws CursorDecodingException if the cursor is malformed or has the
     * wrong cursor type
     */
    public UpdatedAtIdCursor<String> decodeUpdatedAtIdCursor(String cursor) {
        return decodeUpdatedAtIdCursor(cursor, Function.identity());
    }

    /**
     * Decodes an opaque public string as an updated-at plus id cursor.
     *
     * @param cursor opaque cursor string
     * @param idDecoder converts the encoded id text to the caller id type
     * @param <ID> id type
     * @return decoded updated-at plus id cursor
     * @throws CursorDecodingException if the cursor is malformed, has the wrong
     * cursor type, or a cursor value cannot be decoded
     */
    public <ID> UpdatedAtIdCursor<ID> decodeUpdatedAtIdCursor(
            String cursor,
            Function<String, ID> idDecoder
    ) {
        Objects.requireNonNull(idDecoder, "idDecoder must not be null");
        String[] parts = decodeParts(cursor, UPDATED_AT_ID_TYPE, 3);
        return new UpdatedAtIdCursor<>(
                decodeInstant(parts[1]),
                decodeId(parts[2], idDecoder)
        );
    }

    private String encodePayload(String payload) {
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeField(Object value) {
        Object nonNullValue = Objects.requireNonNull(value, "cursor values must not be null");
        if (!(nonNullValue instanceof CharSequence
                || nonNullValue instanceof UUID
                || nonNullValue instanceof Instant
                || nonNullValue instanceof Number
                || nonNullValue instanceof Boolean
                || nonNullValue instanceof Enum<?>)) {
            throw new IllegalArgumentException(
                    "Cursor value type " + nonNullValue.getClass().getName()
                            + " is not supported by the default encoder; provide an explicit id encoder"
            );
        }
        return encodeStringField(nonNullValue.toString());
    }

    private String encodeStringField(String value) {
        return ENCODER.encodeToString(
                Objects.requireNonNull(value, "cursor values must not be null")
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    private String[] decodeParts(String cursor, String expectedType, int expectedPartCount) {
        Objects.requireNonNull(cursor, CURSOR_MUST_NOT_BE_NULL);
        String payload = decodeBase64(cursor, "Malformed cursor: value is not valid Base64-url");
        String[] parts = payload.split("\n", -1);
        String actualType = parts[0];
        if (!expectedType.equals(actualType)) {
            throw new CursorDecodingException("Cursor type mismatch: expected "
                    + expectedType + " but found " + actualType);
        }
        if (parts.length != expectedPartCount) {
            throw new CursorDecodingException("Malformed cursor: expected "
                    + expectedPartCount + " parts but found " + parts.length);
        }
        return parts;
    }

    private String decodeBase64(String value, String errorMessage) {
        try {
            return new String(DECODER.decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new CursorDecodingException(errorMessage, exception);
        }
    }

    private Instant decodeInstant(String value) {
        String text = decodeBase64(value, "Malformed cursor: updatedAt is not valid Base64-url");
        try {
            return Instant.parse(text);
        } catch (DateTimeException exception) {
            throw new CursorDecodingException("Malformed cursor: updatedAt is not a valid Instant", exception);
        }
    }

    private <ID> ID decodeId(String value, Function<String, ID> idDecoder) {
        String text = decodeBase64(value, "Malformed cursor: id is not valid Base64-url");
        try {
            ID id = idDecoder.apply(text);
            if (id == null) {
                throw new CursorDecodingException("Malformed cursor: decoded id must not be null");
            }
            return id;
        } catch (CursorDecodingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CursorDecodingException("Malformed cursor: id cannot be decoded", exception);
        }
    }
}
