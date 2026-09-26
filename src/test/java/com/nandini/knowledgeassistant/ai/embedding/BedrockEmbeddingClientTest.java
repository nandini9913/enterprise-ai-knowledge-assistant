package com.nandini.knowledgeassistant.ai.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nandini.knowledgeassistant.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    private final BedrockEmbeddingClient client = new BedrockEmbeddingClient(bedrock, objectMapper, new AiProperties(
            new AiProperties.Embedding(AiProperties.Provider.BEDROCK, 3, "amazon.titan-embed-text-v2:0", 8),
            new AiProperties.Chat(AiProperties.Provider.LOCAL, null, 512, Duration.ofSeconds(5), 1)));

    @Test
    @SuppressWarnings("unchecked")
    void requestsNormalizedTitanEmbeddingsWithConfiguredDimensions() throws Exception {
        when(bedrock.invokeModel(any(Consumer.class))).thenReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embedding\":[0.6,0.8,0.0],\"inputTextTokenCount\":3}"))
                .build());

        List<float[]> vectors = client.embed(List.of("hello world"));

        assertThat(vectors).singleElement().satisfies(v -> assertThat(v).containsExactly(0.6f, 0.8f, 0.0f));
        ArgumentCaptor<Consumer<InvokeModelRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).invokeModel(captor.capture());
        InvokeModelRequest.Builder builder = InvokeModelRequest.builder();
        captor.getValue().accept(builder);
        InvokeModelRequest request = builder.build();
        assertThat(request.modelId()).isEqualTo("amazon.titan-embed-text-v2:0");
        var body = objectMapper.readTree(request.body().asUtf8String());
        assertThat(body.get("inputText").asText()).isEqualTo("hello world");
        assertThat(body.get("dimensions").asInt()).isEqualTo(3);
        assertThat(body.get("normalize").asBoolean()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dimensionMismatchIsDetected() {
        when(bedrock.invokeModel(any(Consumer.class))).thenReturn(InvokeModelResponse.builder()
                .body(SdkBytes.fromUtf8String("{\"embedding\":[0.1,0.2]}")).build());

        assertThatThrownBy(() -> client.embed("text")).isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("Expected 3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sdkFailuresBecomeEmbeddingExceptions() {
        when(bedrock.invokeModel(any(Consumer.class))).thenThrow(ThrottlingException.builder().message("x").build());

        assertThatThrownBy(() -> client.embed("text")).isInstanceOf(EmbeddingException.class);
    }
}
