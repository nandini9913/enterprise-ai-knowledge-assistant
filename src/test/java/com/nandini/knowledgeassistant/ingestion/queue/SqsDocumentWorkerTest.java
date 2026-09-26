package com.nandini.knowledgeassistant.ingestion.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nandini.knowledgeassistant.config.ProcessingProperties;
import com.nandini.knowledgeassistant.ingestion.DocumentProcessor;
import com.nandini.knowledgeassistant.ingestion.ProcessingOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SqsDocumentWorkerTest {

    private final SqsClient sqs = mock(SqsClient.class);
    private final DocumentProcessor processor = mock(DocumentProcessor.class);
    private final UUID documentId = UUID.randomUUID();
    private final SqsDocumentWorker worker = new SqsDocumentWorker(sqs,
            new ObjectMapper().registerModule(new JavaTimeModule()), processor,
            new ProcessingProperties(ProcessingProperties.Mode.SQS, false, Duration.ofMinutes(15),
                    new ProcessingProperties.Sqs("http://queue", true, 0, 5, 3, Duration.ofSeconds(30))));

    @Test
    @SuppressWarnings("unchecked")
    void completedMessagesAreDeleted() {
        when(processor.process(documentId, false)).thenReturn(ProcessingOutcome.COMPLETED);

        worker.handle(message(1));

        verify(sqs).deleteMessage(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void transientFailuresBackOffExponentiallyWithoutDeleting() {
        when(processor.process(documentId, false)).thenReturn(ProcessingOutcome.RETRY);

        worker.handle(message(2));

        verify(sqs, never()).deleteMessage(any(Consumer.class));
        ArgumentCaptor<Consumer<ChangeMessageVisibilityRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(sqs).changeMessageVisibility(captor.capture());
        ChangeMessageVisibilityRequest.Builder builder = ChangeMessageVisibilityRequest.builder();
        captor.getValue().accept(builder);
        assertThat(builder.build().visibilityTimeout()).isEqualTo(60); // 30s * 2^(2-1)
    }

    @Test
    @SuppressWarnings("unchecked")
    void lastAttemptIsFlaggedAndItsFailedMessageIsLeftForTheDeadLetterQueue() {
        when(processor.process(eq(documentId), eq(true))).thenReturn(ProcessingOutcome.FAILED);

        worker.handle(message(3));

        verify(processor).process(documentId, true);
        verify(sqs, never()).deleteMessage(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void permanentFailuresBeforeTheLastAttemptAreDeleted() {
        when(processor.process(documentId, false)).thenReturn(ProcessingOutcome.FAILED);

        worker.handle(message(1));

        verify(sqs).deleteMessage(any(Consumer.class));
    }

    @Test
    void unreadableMessagesAreLeftForTheDeadLetterQueue() {
        worker.handle(Message.builder().messageId("m").receiptHandle("r").body("{broken").build());

        verifyNoInteractions(processor);
        verifyNoInteractions(sqs);
    }

    private Message message(int receiveCount) {
        return Message.builder()
                .messageId("m-" + receiveCount)
                .receiptHandle("receipt")
                .body("{\"type\":\"DocumentProcessingRequested\",\"documentId\":\"" + documentId
                        + "\",\"requestId\":\"req-1\",\"requestedAt\":\"2026-01-01T00:00:00Z\"}")
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(receiveCount)))
                .build();
    }
}
