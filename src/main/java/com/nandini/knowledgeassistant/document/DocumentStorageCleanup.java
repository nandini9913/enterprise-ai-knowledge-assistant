package com.nandini.knowledgeassistant.document;

import com.nandini.knowledgeassistant.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Removes stored objects only after the delete transaction commits. If the commit fails,
 * the file is still there; if the object delete fails, an orphaned object is left behind,
 * which is harmless and can be swept by an S3 lifecycle rule.
 */
@Component
public class DocumentStorageCleanup {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageCleanup.class);

    private final DocumentStorage storage;

    public DocumentStorageCleanup(DocumentStorage storage) {
        this.storage = storage;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeleted(DocumentEvents.Deleted event) {
        try {
            storage.delete(event.storageKey());
        } catch (RuntimeException ex) {
            log.warn("Could not delete stored object for document {}", event.documentId(), ex);
        }
    }
}
