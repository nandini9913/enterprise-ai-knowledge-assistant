package com.nandini.knowledgeassistant.ai.embedding;

import com.nandini.knowledgeassistant.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashEmbeddingClientTest {

    private final LocalHashEmbeddingClient client = new LocalHashEmbeddingClient(new AiProperties(
            new AiProperties.Embedding(AiProperties.Provider.LOCAL, 256, null, 8),
            new AiProperties.Chat(AiProperties.Provider.LOCAL, null, 512, Duration.ofSeconds(5), 1)));

    @Test
    void vectorsHaveConfiguredDimensionsAndUnitLength() {
        float[] vector = client.embed("Remote work policy for employees");

        assertThat(vector).hasSize(256);
        assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void relatedTextIsMoreSimilarThanUnrelatedText() {
        float[] question = client.embed("What is the hotel reimbursement limit?");
        float[] related = client.embed("Hotel reimbursement is limited to 180 dollars per night.");
        float[] unrelated = client.embed("Laptops require full disk encryption.");

        assertThat(cosine(question, related)).isGreaterThan(cosine(question, unrelated));
    }

    @Test
    void embeddingIsDeterministicAndEmptyTextStillHasADirection() {
        assertThat(client.embed("same text")).isEqualTo(client.embed("same text"));
        assertThat(norm(client.embed("the and of"))).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot; // both vectors are unit length
    }

    private static double norm(float[] v) {
        double sum = 0;
        for (float x : v) {
            sum += x * x;
        }
        return Math.sqrt(sum);
    }
}
