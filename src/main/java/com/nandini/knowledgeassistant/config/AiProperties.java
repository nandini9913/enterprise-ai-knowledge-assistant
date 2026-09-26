package com.nandini.knowledgeassistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Model configuration. Model ids are configuration, not code, so switching models
 * (or regions / inference profiles) needs no rebuild.
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(@Valid @NotNull Embedding embedding, @Valid @NotNull Chat chat) {

    public enum Provider { LOCAL, BEDROCK }

    public record Embedding(
            @NotNull Provider provider,
            @Min(1) int dimensions,
            String bedrockModelId,
            @Min(1) int batchSize) {
    }

    public record Chat(
            @NotNull Provider provider,
            String bedrockModelId,
            @Min(64) int maxTokens,
            @NotNull Duration timeout,
            @Min(1) int maxAttempts) {
    }
}
