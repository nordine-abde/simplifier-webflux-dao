package com.anordine.simplifier.webflux.dao.cursor;

/**
 * Raised when an opaque cursor string cannot be decoded as the expected cursor
 * value type.
 */
public class CursorDecodingException extends IllegalArgumentException {

    /**
     * Creates a cursor decoding exception with a message.
     *
     * @param message failure detail
     */
    public CursorDecodingException(String message) {
        super(message);
    }

    /**
     * Creates a cursor decoding exception with a message and cause.
     *
     * @param message failure detail
     * @param cause underlying decoding failure
     */
    public CursorDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
