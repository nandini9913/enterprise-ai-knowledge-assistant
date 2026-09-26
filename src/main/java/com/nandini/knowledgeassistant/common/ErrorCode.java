package com.nandini.knowledgeassistant.common;

import org.springframework.http.HttpStatus;

/**
 * Stable, client-facing error codes. Clients and dashboards key off these values,
 * so they must never be renamed once released.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    DOCUMENT_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    DOCUMENT_EMPTY(HttpStatus.BAD_REQUEST),
    DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    DOCUMENT_ALREADY_EXISTS(HttpStatus.CONFLICT),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_READY(HttpStatus.CONFLICT),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    QUESTION_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    MODEL_THROTTLED(HttpStatus.SERVICE_UNAVAILABLE),
    MODEL_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
