package com.accounting.qbo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API response wrapper for all REST endpoints.
 *
 * <p>All data (list or single item) is held in the {@code data} field.
 * Use {@link #ofList} for collections and {@link #ofOne} for single items.
 *
 * <pre>
 * List:   { "success": true, "count": 5, "data": [...], "timestamp": "..." }
 * Single: { "success": true, "count": 1, "data": {...}, "timestamp": "..." }
 * Error:  { "success": false, "message": "...", "timestamp": "..." }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        Integer count,
        T data,
        Instant timestamp
) {
    /** Success response wrapping a list. T becomes List&lt;E&gt; at the call site. */
    public static <E> ApiResponse<List<E>> ofList(List<E> items) {
        return new ApiResponse<>(true, null, items.size(), items, Instant.now());
    }

    /** Success response wrapping a single item. */
    public static <T> ApiResponse<T> ofOne(T data) {
        return new ApiResponse<>(true, null, 1, data, Instant.now());
    }

    /** Success response with a message only (e.g., for auth / admin operations). */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null, null, Instant.now());
    }

    /** Error response. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, Instant.now());
    }
}
