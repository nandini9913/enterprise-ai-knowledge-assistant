package com.nandini.knowledgeassistant.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@NotNull Provider provider, String localPath, String s3Bucket) {

    public enum Provider { LOCAL, S3 }

    @AssertTrue(message = "app.storage.s3-bucket is required when provider is S3; local-path when provider is LOCAL")
    public boolean isProviderConfigured() {
        return switch (provider == null ? Provider.LOCAL : provider) {
            case S3 -> s3Bucket != null && !s3Bucket.isBlank();
            case LOCAL -> localPath != null && !localPath.isBlank();
        };
    }
}
