package com.nandini.knowledgeassistant.ingestion.queue;

import java.time.Instant;
import java.util.UUID;

/**
 * SQS message body. It carries identifiers only - never document content - which keeps
 * messages small (SQS limit: 256 KB) and keeps sensitive data out of the queue.
 */
public record ProcessingMessage(String type, UUID documentId, String requestId, Instant requestedAt) {

    public static final String TYPE = "DocumentProcessingRequested";
}
