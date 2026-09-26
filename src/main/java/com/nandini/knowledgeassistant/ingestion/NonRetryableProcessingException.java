package com.nandini.knowledgeassistant.ingestion;

/**
 * A processing failure that will fail again on every retry (corrupt file, encrypted PDF,
 * no extractable text). The document is marked FAILED immediately instead of being retried.
 */
public class NonRetryableProcessingException extends RuntimeException {

    public NonRetryableProcessingException(String message) {
        super(message);
    }

    public NonRetryableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
