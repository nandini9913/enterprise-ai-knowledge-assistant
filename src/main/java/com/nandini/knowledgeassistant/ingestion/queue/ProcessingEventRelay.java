package com.nandini.knowledgeassistant.ingestion.queue;

import com.nandini.knowledgeassistant.document.DocumentEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispatches processing only after the upload transaction has committed.
 * <p>
 * If dispatch fails (e.g. SQS unavailable), the document stays UPLOADED and the owner can
 * trigger reprocessing. A transactional outbox table would close this gap completely; it is
 * a documented next step.
 */
@Component
public class ProcessingEventRelay {

    private static final Logger log = LoggerFactory.getLogger(ProcessingEventRelay.class);

    private final ProcessingDispatcher dispatcher;

    public ProcessingEventRelay(ProcessingDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessingRequested(DocumentEvents.ProcessingRequested event) {
        try {
            dispatcher.dispatch(event);
        } catch (RuntimeException ex) {
            log.error("Could not dispatch processing for document {}; it remains UPLOADED", event.documentId(), ex);
        }
    }
}
