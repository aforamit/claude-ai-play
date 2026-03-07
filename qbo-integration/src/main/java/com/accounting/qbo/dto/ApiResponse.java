package com.accounting.qbo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API response wrapper for all REST endpoints.
 *
 * <p>Provides consistent structure:
 * <pre>
 * {
 *   "success": true,
 *   "count": 5,
 *   "data": [...],
 *   "timestamp": "2024-01-15T10:00:00Z"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        Integer count,
        T data,
        List<T> items,
        Instant timestamp
) {
    /** Success response with a list of items. */
    public static <T> ApiResponse<T> ok(List<T> items) {
        return new ApiResponse<>(true, null, items.size(), null, items, Instant.now());
    }

    /** Success response with a single item. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, 1, data, null, Instant.now());
    }

    /** Success response with a message (e.g., for auth operations). */
    public static <T> ApiResponse<T> ok(String message) {
        return new ApiResponse<>(true, message, null, null, null, Instant.now());
    }

    /** Error response. */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, null, Instant.now());
    }
}
