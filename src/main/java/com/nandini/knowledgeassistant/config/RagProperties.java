package com.nandini.knowledgeassistant.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retrieval and guardrail limits for the question-answering flow.
 * {@code requireCitations}: withhold any model answer that does not cite a provided source.
 */
@Validated
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(
        @Min(1) @Max(20) int topK,
        @Min(1) @Max(50) int maxTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") double minSimilarity,
        @Min(500) int maxContextChars,
        @Min(10) int maxQuestionLength,
        @Min(200) int maxAnswerChars,
        boolean redactPii,
        boolean requireCitations) {
}
