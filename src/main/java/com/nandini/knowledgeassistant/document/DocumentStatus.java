package com.nandini.knowledgeassistant.document;

/**
 * Document lifecycle.
 * <pre>
 * UPLOADED --claim--> PROCESSING --success--> READY
 *    ^                    |
 *    +---- retryable -----+----- retries exhausted / permanent error --> FAILED
 * </pre>
 * READY and FAILED documents return to UPLOADED when reprocessing is requested.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
