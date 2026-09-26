package com.nandini.knowledgeassistant.ai.embedding;

/** A (usually transient) failure of the embedding model, e.g. throttling or a timeout. */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmbeddingException(String message) {
        super(message);
    }
}
