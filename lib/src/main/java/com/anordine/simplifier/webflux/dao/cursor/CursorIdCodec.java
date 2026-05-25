package com.anordine.simplifier.webflux.dao.cursor;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Encodes and decodes entity ids stored inside opaque cursor strings.
 *
 * @param <ID> entity identifier type
 */
public final class CursorIdCodec<ID> {

    private final Function<ID, String> encoder;
    private final Function<String, ID> decoder;

    private CursorIdCodec(Function<ID, String> encoder, Function<String, ID> decoder) {
        this.encoder = Objects.requireNonNull(encoder, "encoder must not be null");
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
    }

    /**
     * Creates an id codec from explicit encoder and decoder functions.
     *
     * @param encoder converts an id to stable cursor text
     * @param decoder converts cursor text back to an id
     * @param <ID> id type
     * @return id codec
     */
    public static <ID> CursorIdCodec<ID> of(
            Function<ID, String> encoder,
            Function<String, ID> decoder
    ) {
        return new CursorIdCodec<>(encoder, decoder);
    }

    /**
     * Creates a UUID id codec.
     *
     * @return UUID id codec
     */
    public static CursorIdCodec<UUID> uuid() {
        return of(UUID::toString, UUID::fromString);
    }

    /**
     * Creates a String id codec.
     *
     * @return String id codec
     */
    public static CursorIdCodec<String> string() {
        return of(Function.identity(), Function.identity());
    }

    /**
     * Creates a Long id codec.
     *
     * @return Long id codec
     */
    public static CursorIdCodec<Long> longId() {
        return of(String::valueOf, Long::valueOf);
    }

    /**
     * Creates an Integer id codec.
     *
     * @return Integer id codec
     */
    public static CursorIdCodec<Integer> integerId() {
        return of(String::valueOf, Integer::valueOf);
    }

    /**
     * Encodes an id to stable cursor text.
     *
     * @param id id to encode
     * @return encoded id text
     */
    public String encode(ID id) {
        String encoded = encoder.apply(Objects.requireNonNull(id, "id must not be null"));
        if (encoded == null || encoded.isEmpty()) {
            throw new CursorDecodingException("Cursor id encoder must return non-empty text");
        }
        return encoded;
    }

    /**
     * Decodes cursor id text.
     *
     * @param value encoded id text
     * @return decoded id
     */
    public ID decode(String value) {
        ID decoded = decoder.apply(Objects.requireNonNull(value, "value must not be null"));
        if (decoded == null) {
            throw new CursorDecodingException("Cursor id decoder must not return null");
        }
        return decoded;
    }
}
