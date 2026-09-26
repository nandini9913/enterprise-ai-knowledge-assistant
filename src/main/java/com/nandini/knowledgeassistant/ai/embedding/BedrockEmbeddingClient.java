package com.nandini.knowledgeassistant.ai.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nandini.knowledgeassistant.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Amazon Titan Text Embeddings V2 via Bedrock InvokeModel.
 * <p>
 * Titan V2 embeds one text per request, supports 256/512/1024 dimensions, and returns
 * normalized vectors when asked (so cosine similarity equals the dot product).
 */
@Component
@ConditionalOnProperty(name = "app.ai.embedding.provider", havingValue = "bedrock")
public class BedrockEmbeddingClient implements EmbeddingClient {

    private final BedrockRuntimeClient bedrock;
    private final ObjectMapper objectMapper;
    private final String modelId;
    private final int dimensions;

    public BedrockEmbeddingClient(BedrockRuntimeClient bedrock, ObjectMapper objectMapper, AiProperties properties) {
        this.bedrock = bedrock;
        this.objectMapper = objectMapper;
        this.modelId = properties.embedding().bedrockModelId();
        this.dimensions = properties.embedding().dimensions();
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("app.ai.embedding.bedrock-model-id must be set for the bedrock provider");
        }
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return vectors;
    }

    private float[] embedOne(String text) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("inputText", text, "dimensions", dimensions, "normalize", true));
            InvokeModelResponse response = bedrock.invokeModel(request -> request
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body)));
            return parse(response.body().asUtf8String());
        } catch (SdkException ex) {
            throw new EmbeddingException("Bedrock embedding request failed", ex);
        } catch (IOException ex) {
            throw new EmbeddingException("Unexpected Bedrock embedding response", ex);
        }
    }

    float[] parse(String json) throws IOException {
        JsonNode embedding = objectMapper.readTree(json).path("embedding");
        if (!embedding.isArray() || embedding.size() != dimensions) {
            throw new EmbeddingException("Expected " + dimensions + " embedding dimensions but got "
                    + embedding.size());
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelId() {
        return modelId;
    }
}
