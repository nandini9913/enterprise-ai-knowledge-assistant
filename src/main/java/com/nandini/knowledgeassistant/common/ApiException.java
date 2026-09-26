package com.nandini.knowledgeassistant.common;

/**
 * Domain exception carrying a stable {@link ErrorCode}. Services throw this instead of
 * leaking infrastructure exceptions (JDBC, AWS SDK, ...) to the web layer.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
