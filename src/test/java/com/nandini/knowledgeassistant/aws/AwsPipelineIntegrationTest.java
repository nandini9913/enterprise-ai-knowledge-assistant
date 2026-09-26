package com.nandini.knowledgeassistant.aws;

import com.nandini.knowledgeassistant.support.IntegrationTest;
import com.nandini.knowledgeassistant.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production pipeline against LocalStack: the API stores the file in S3 and publishes an
 * SQS message after commit; the worker consumes it, reads the object back from S3, embeds the
 * chunks and marks the document READY.
 */
class AwsPipelineIntegrationTest extends IntegrationTest {

    private static final String BUCKET = "test-documents";
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SQS);
    private static final String QUEUE_URL;
    private static final String DLQ_URL;

    static {
        LOCALSTACK.start();
        try (S3Client s3 = s3(); SqsClient sqs = sqs()) {
            s3.createBucket(b -> b.bucket(BUCKET));
            DLQ_URL = sqs.createQueue(q -> q.queueName("document-processing-dlq")).queueUrl();
            String dlqArn = sqs.getQueueAttributes(a -> a.queueUrl(DLQ_URL)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
            QUEUE_URL = sqs.createQueue(q -> q.queueName("document-processing").attributes(Map.of(
                    QueueAttributeName.VISIBILITY_TIMEOUT, "5",
                    QueueAttributeName.REDRIVE_POLICY,
                    "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"2\"}"))).queueUrl();
        }
    }

    @DynamicPropertySource
    static void awsProperties(DynamicPropertyRegistry registry) {
        registry.add("app.aws.region", LOCALSTACK::getRegion);
        registry.add("app.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("app.storage.provider", () -> "s3");
        registry.add("app.storage.s3-bucket", () -> BUCKET);
        registry.add("app.processing.mode", () -> "sqs");
        registry.add("app.processing.sqs.queue-url", () -> QUEUE_URL);
        registry.add("app.processing.sqs.listener-enabled", () -> "true");
        registry.add("app.processing.sqs.wait-time-seconds", () -> "1");
        registry.add("app.processing.sqs.max-receive-count", () -> "2");
        registry.add("app.processing.sqs.retry-backoff", () -> "PT1S");
        // The SDK's default credential chain reads these system properties.
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    }

    @Autowired
    private S3Client s3Client;

    @Autowired
    private SqsClient sqsClient;

    @Test
    void uploadIsStoredInS3AndProcessedAsynchronouslyViaSqs() throws Exception {
        TestUser owner = newUser("aws");
        UUID id = uploadReady(owner, "benefits.pdf", TestDocuments.pdf(List.of(
                "Benefits guide. The annual learning budget is 1500 dollars per employee.")));

        var objects = s3Client.listObjectsV2(l -> l.bucket(BUCKET).prefix("documents/" + owner.id())).contents();
        assertThat(objects).singleElement().satisfies(o -> assertThat(o.key()).endsWith(id + ".pdf"));

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                        .andExpect(jsonPath("$.status").value("READY")));

        mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "What is the annual learning budget?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.citations[0].documentId").value(id.toString()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(visibleMessages(QUEUE_URL)).isZero());
    }

    @Test
    void poisonMessagesEndUpInTheDeadLetterQueue() {
        sqsClient.sendMessage(m -> m.queueUrl(QUEUE_URL).messageBody("{not valid json"));

        // Not deleted by the worker; after maxReceiveCount receives SQS moves it to the DLQ.
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(visibleMessages(DLQ_URL)).isEqualTo(1));
    }

    private int visibleMessages(String queueUrl) {
        return Integer.parseInt(sqsClient.getQueueAttributes(a -> a.queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    private static S3Client s3() {
        return S3Client.builder().endpointOverride(LOCALSTACK.getEndpoint()).forcePathStyle(true)
                .region(software.amazon.awssdk.regions.Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials()).build();
    }

    private static SqsClient sqs() {
        return SqsClient.builder().endpointOverride(LOCALSTACK.getEndpoint())
                .region(software.amazon.awssdk.regions.Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(credentials()).build();
    }

    private static software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentials() {
        return software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    }
}
