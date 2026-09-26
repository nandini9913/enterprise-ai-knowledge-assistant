package com.nandini.knowledgeassistant.ingestion.queue;

import com.nandini.knowledgeassistant.common.RequestIds;
import com.nandini.knowledgeassistant.config.ProcessingProperties;
import com.nandini.knowledgeassistant.document.DocumentEvents;
import com.nandini.knowledgeassistant.ingestion.DocumentProcessor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Processes documents in the same JVM. With {@code inline-async=true} work runs on a
 * background executor (local development); with {@code false} it runs synchronously, which
 * makes integration tests deterministic.
 */
@Component
@ConditionalOnProperty(name = "app.processing.mode", havingValue = "inline", matchIfMissing = true)
public class InlineProcessingDispatcher implements ProcessingDispatcher {

    private final DocumentProcessor processor;
    private final TaskExecutor executor;
    private final boolean async;

    public InlineProcessingDispatcher(DocumentProcessor processor, TaskExecutor applicationTaskExecutor,
                                      ProcessingProperties properties) {
        this.processor = processor;
        this.executor = applicationTaskExecutor;
        this.async = properties.inlineAsync();
    }

    @Override
    public void dispatch(DocumentEvents.ProcessingRequested request) {
        Runnable work = () -> {
            MDC.put(RequestIds.MDC_KEY, RequestIds.sanitizeOrGenerate(request.requestId()));
            try {
                processor.process(request.documentId(), true);
            } finally {
                MDC.remove(RequestIds.MDC_KEY);
            }
        };
        if (async) {
            executor.execute(work);
        } else {
            String callerRequestId = MDC.get(RequestIds.MDC_KEY);
            work.run();
            if (callerRequestId != null) {
                MDC.put(RequestIds.MDC_KEY, callerRequestId);
            }
        }
    }
}
