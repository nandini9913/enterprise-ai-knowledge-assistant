package com.nandini.knowledgeassistant.ingestion.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nandini.knowledgeassistant.config.ProcessingProperties;
import com.nandini.knowledgeassistant.document.DocumentEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Clock;

/** Publishes a processing request to SQS for the worker to consume. */
@Component
@ConditionalOnProperty(name = "app.processing.mode", havingValue = "sqs")
public class SqsProcessingDispatcher implements ProcessingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SqsProcessingDispatcher.class);

    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final Clock clock;

    public SqsProcessingDispatcher(SqsClient sqs, ObjectMapper objectMapper, ProcessingProperties properties,
                                   Clock clock) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.queueUrl = properties.sqs().queueUrl();
        this.clock = clock;
    }

    @Override
    public void dispatch(DocumentEvents.ProcessingRequested request) {
        ProcessingMessage message = new ProcessingMessage(ProcessingMessage.TYPE, request.documentId(),
                request.requestId(), clock.instant());
        String body = toJson(message);
        String messageId = sqs.sendMessage(send -> send.queueUrl(queueUrl).messageBody(body)).messageId();
        log.info("Queued document {} for processing (message {})", request.documentId(), messageId);
    }

    private String toJson(ProcessingMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize processing message", ex);
        }
    }
}
