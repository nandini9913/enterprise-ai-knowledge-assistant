package com.nandini.knowledgeassistant.ingestion.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nandini.knowledgeassistant.common.RequestIds;
import com.nandini.knowledgeassistant.config.ProcessingProperties;
import com.nandini.knowledgeassistant.ingestion.DocumentProcessor;
import com.nandini.knowledgeassistant.ingestion.ProcessingOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Long-polling SQS consumer.
 * <p>
 * Retry semantics rely on SQS itself:
 * <ul>
 *   <li>Success or a permanent failure (corrupt file): the message is deleted.</li>
 *   <li>Transient failure: the message is <em>not</em> deleted; its visibility timeout is
 *       shortened to a backoff delay so SQS redelivers it.</li>
 *   <li>After {@code maxReceiveCount} receives the queue's redrive policy moves the message
 *       to the dead-letter queue. On that last attempt the worker marks the document FAILED
 *       and leaves the message for the redrive, so the DLQ keeps the evidence.</li>
 * </ul>
 * Enabled only when {@code app.processing.sqs.listener-enabled=true}, so the API and the
 * worker can be deployed and scaled separately from the same image.
 */
@Component
@ConditionalOnExpression("'${app.processing.mode}'.equalsIgnoreCase('sqs') "
        + "and ${app.processing.sqs.listener-enabled:false}")
public class SqsDocumentWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsDocumentWorker.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final DocumentProcessor processor;
    private final ProcessingProperties.Sqs properties;
    private ExecutorService executor;
    private volatile boolean running;

    public SqsDocumentWorker(SqsClient sqs, ObjectMapper objectMapper, DocumentProcessor processor,
                             ProcessingProperties processingProperties) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.properties = processingProperties.sqs();
    }

    @Override
    public void start() {
        running = true;
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sqs-document-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::pollLoop);
        log.info("SQS document worker started for {}", properties.queueUrl());
    }

    @Override
    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdown();
            try {
                // Let an in-flight message finish; unfinished messages simply become visible again.
                if (!executor.awaitTermination(properties.waitTimeSeconds() + 10L, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop() {
        while (running) {
            try {
                pollOnce();
            } catch (RuntimeException ex) {
                log.error("SQS polling failed; backing off", ex);
                sleepQuietly(5_000);
            }
        }
    }

    /** Receives and handles one batch. Package-private so tests can drive it deterministically. */
    int pollOnce() {
        List<Message> messages = sqs.receiveMessage(receive -> receive
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .messages();
        messages.forEach(this::handle);
        return messages.size();
    }

    void handle(Message message) {
        int receiveCount = Integer.parseInt(message.attributes()
                .getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"));
        boolean finalAttempt = receiveCount >= properties.maxReceiveCount();
        ProcessingMessage body;
        try {
            body = objectMapper.readValue(message.body(), ProcessingMessage.class);
        } catch (Exception ex) {
            // Poison message: leave it; the redrive policy moves it to the DLQ for inspection.
            log.error("Unreadable SQS message {} (receive {}); leaving for DLQ", message.messageId(), receiveCount);
            return;
        }
        MDC.put(RequestIds.MDC_KEY, RequestIds.sanitizeOrGenerate(body.requestId()));
        try {
            ProcessingOutcome outcome = processor.process(body.documentId(), finalAttempt);
            switch (outcome) {
                case COMPLETED, SKIPPED -> delete(message);
                // On the final attempt keep the message so the redrive policy moves it to the DLQ.
                case FAILED -> {
                    if (!finalAttempt) {
                        delete(message);
                    }
                }
                case RETRY -> backoff(message, receiveCount);
            }
        } catch (RuntimeException ex) {
            log.error("Unexpected error handling message {}", message.messageId(), ex);
            if (finalAttempt) {
                processor.markFailed(body.documentId(), "Processing failed after retries");
            }
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private void delete(Message message) {
        sqs.deleteMessage(delete -> delete.queueUrl(properties.queueUrl()).receiptHandle(message.receiptHandle()));
    }

    /** Exponential backoff via the visibility timeout: base * 2^(attempt-1), capped at 12 hours. */
    private void backoff(Message message, int receiveCount) {
        long seconds = Math.min(properties.retryBackoff().toSeconds() * (1L << Math.min(receiveCount - 1, 10)),
                43_200L);
        sqs.changeMessageVisibility(change -> change.queueUrl(properties.queueUrl())
                .receiptHandle(message.receiptHandle())
                .visibilityTimeout((int) seconds));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
