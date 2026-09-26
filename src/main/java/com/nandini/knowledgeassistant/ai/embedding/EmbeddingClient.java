package com.nandini.knowledgeassistant.ai.embedding;

import java.util.List;

/**
 * Converts text into dense vectors. The same client (and model) must be used for documents
 * and questions: vectors from different models live in different spaces and cannot be compared.
 */
public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {
        return embed(List.of(text)).get(0);
    }

    int dimensions();

    String modelId();
}
