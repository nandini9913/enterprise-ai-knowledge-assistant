package com.nandini.knowledgeassistant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * AWS SDK v2 clients. Each client is created only when the feature that needs it is enabled,
 * so local development runs without any AWS account.
 */
@Configuration(proxyBeanMethods = false)
public class AwsConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
    S3Client s3Client(AwsProperties aws) {
        var builder = S3Client.builder().region(Region.of(aws.region()));
        if (aws.endpoint() != null) {
            // LocalStack serves buckets by path, not virtual-host subdomain.
            builder.endpointOverride(aws.endpoint()).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.processing.mode", havingValue = "sqs")
    SqsClient sqsClient(AwsProperties aws) {
        var builder = SqsClient.builder().region(Region.of(aws.region()));
        if (aws.endpoint() != null) {
            builder.endpointOverride(aws.endpoint());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${app.ai.chat.provider}'.equalsIgnoreCase('bedrock') "
            + "or '${app.ai.embedding.provider}'.equalsIgnoreCase('bedrock')")
    BedrockRuntimeClient bedrockRuntimeClient(AwsProperties aws, AiProperties ai) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(aws.region()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        // Standard strategy retries throttling and 5xx with exponential backoff and jitter.
                        .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder()
                                .maxAttempts(ai.chat().maxAttempts())
                                .build())
                        .apiCallAttemptTimeout(ai.chat().timeout())
                        .apiCallTimeout(ai.chat().timeout().multipliedBy(ai.chat().maxAttempts()))
                        .build())
                .build();
    }
}
