package com.nandini.knowledgeassistant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(@NotNull DataSize maxFileSize, @Min(1) int maxPages) {
}
