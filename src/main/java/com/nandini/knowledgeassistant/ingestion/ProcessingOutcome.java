package com.nandini.knowledgeassistant.ingestion;

public enum ProcessingOutcome {
    /** Chunks and embeddings stored; the document is READY. */
    COMPLETED,
    /** Nothing to do: already READY, deleted, or claimed by another worker. */
    SKIPPED,
    /** Transient failure; the message should be redelivered. */
    RETRY,
    /** Permanent failure or retries exhausted; the document is FAILED. */
    FAILED
}
