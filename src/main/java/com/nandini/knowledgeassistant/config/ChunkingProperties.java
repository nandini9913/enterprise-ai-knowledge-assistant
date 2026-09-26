package com.nandini.knowledgeassistant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Chunk sizes are measured in characters; roughly 4 characters per token for English text. */
@Validated
@ConfigurationProperties(prefix = "app.chunking")
public record ChunkingProperties(@Min(100) int targetSize, @Min(0) int overlap, @Min(1) int minChunkSize) {

    @AssertTrue(message = "app.chunking.overlap must be smaller than half of target-size")
    public boolean isOverlapValid() {
        return overlap < targetSize / 2;
    }
}
