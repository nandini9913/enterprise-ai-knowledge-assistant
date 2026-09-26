package com.nandini.knowledgeassistant.document;

import java.util.UUID;

/** Domain events published inside a transaction and handled after it commits. */
public final class DocumentEvents {

    private DocumentEvents() {
    }

    /** A document is ready to be (re)processed by the ingestion pipeline. */
    public record ProcessingRequested(UUID documentId, String requestId) {
    }

    /** A document row was deleted; its stored object should be removed. */
    public record Deleted(UUID documentId, String storageKey) {
    }
}
