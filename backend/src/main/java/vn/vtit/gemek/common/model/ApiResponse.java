/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.common.model;

import lombok.Getter;

/**
 * Standard single-object API response wrapper.
 *
 * <p>Wraps a single data payload with a success flag, allowing clients to
 * differentiate between a successful response and an error response by shape alone.
 *
 * @param <T> the type of the payload.
 */
@Getter
public class ApiResponse<T> {

    /** Indicates whether the request succeeded. Always {@code true} for this wrapper. */
    private final boolean success;

    /** The response payload. */
    private final T data;

    /**
     * Constructs a successful {@link ApiResponse} wrapping the given data.
     *
     * @param data the payload to wrap.
     */
    private ApiResponse(T data) {
        this.success = true;
        this.data = data;
    }

    /**
     * Factory method creating a successful response wrapping the given data.
     *
     * @param data the payload.
     * @param <T>  the payload type.
     * @return a successful {@link ApiResponse}.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }
}
