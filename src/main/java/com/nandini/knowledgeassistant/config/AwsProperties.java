package com.nandini.knowledgeassistant.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * AWS client settings. Credentials are intentionally absent: the SDK's default provider
 * chain resolves them from environment variables, a profile, or (in AWS) the IAM role.
 * {@code endpoint} is only set for LocalStack.
 */
@Validated
@ConfigurationProperties(prefix = "app.aws")
public record AwsProperties(@NotBlank String region, URI endpoint) {
}
