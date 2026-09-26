package com.nandini.knowledgeassistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How uploaded documents reach the ingestion pipeline.
 * <ul>
 *   <li>{@code INLINE} - processed in-process right after the upload commits (local dev, tests).</li>
 *   <li>{@code SQS} - an event is published to SQS and a worker consumes it (production).</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.processing")
public record ProcessingProperties(
        @NotNull Mode mode,
        boolean inlineAsync,
        @NotNull Duration leaseTimeout,
        @Valid @NotNull Sqs sqs) {

    public enum Mode { INLINE, SQS }

    public record Sqs(
            String queueUrl,
            boolean listenerEnabled,
            @Min(0) @Max(20) int waitTimeSeconds,
            @Min(1) @Max(10) int maxMessages,
            @Min(1) int maxReceiveCount,
            @NotNull Duration retryBackoff) {
    }

    @AssertTrue(message = "app.processing.sqs.queue-url is required when mode is SQS")
    public boolean isQueueConfigured() {
        return mode != Mode.SQS || (sqs != null && sqs.queueUrl() != null && !sqs.queueUrl().isBlank());
    }
}
