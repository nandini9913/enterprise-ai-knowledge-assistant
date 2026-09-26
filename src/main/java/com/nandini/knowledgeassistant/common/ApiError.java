package com.nandini.knowledgeassistant.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** The single error shape returned by every endpoint. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
                RequestIds.current(), List.of());
    }

    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> fieldErrors) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
                RequestIds.current(), fieldErrors);
    }
}
