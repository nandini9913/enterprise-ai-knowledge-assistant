package com.nandini.knowledgeassistant.ai.embedding;

import com.nandini.knowledgeassistant.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline embedding for local development and tests - no AWS account needed.
 * <p>
 * Uses the "hashing trick": each word and word pair is hashed to one of N dimensions with
 * a +/- sign, and the vector is L2-normalized. Cosine similarity then reflects lexical
 * overlap. It captures no real semantics (synonyms are unrelated), but it is deterministic,
 * fast, and exercises exactly the same storage and pgvector search path as a real model.
 */
@Component
@ConditionalOnProperty(name = "app.ai.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from", "has", "have",
            "how", "i", "in", "is", "it", "its", "of", "on", "or", "our", "that", "the", "their", "this", "to",
            "was", "we", "what", "when", "where", "which", "who", "will", "with", "you", "your");

    private final int dimensions;

    public LocalHashEmbeddingClient(AiProperties properties) {
        this.dimensions = properties.embedding().dimensions();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return vectors;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelId() {
        return "local-hash-" + dimensions;
    }

    private float[] embedOne(String text) {
        float[] vector = new float[dimensions];
        List<String> terms = terms(text);
        for (int i = 0; i < terms.size(); i++) {
            add(vector, terms.get(i), 1.0f);
            if (i + 1 < terms.size()) {
                add(vector, terms.get(i) + "_" + terms.get(i + 1), 0.5f);
            }
        }
        normalize(vector);
        return vector;
    }

    /** Lower-cased, stop-word-filtered, lightly stemmed terms. Shared with the local chat model. */
    public static List<String> terms(String text) {
        List<String> terms = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                terms.add(stem(token));
            }
        }
        return terms;
    }

    /** Tiny suffix stripper so "policies"/"policy" and "documents"/"document" collide. */
    private static String stem(String token) {
        if (token.length() > 5 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 5 && token.endsWith("ing")) {
            return token.substring(0, token.length() - 3);
        }
        if (token.length() > 4 && token.endsWith("ed")) {
            return token.substring(0, token.length() - 2);
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private void add(float[] vector, String feature, float weight) {
        int hash = fnv1a(feature);
        int index = Math.floorMod(hash, dimensions);
        float sign = ((hash >>> 31) == 0) ? 1f : -1f;
        vector[index] += sign * weight;
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum == 0) {
            // A zero vector has no direction (cosine distance undefined); use a fixed unit vector.
            vector[0] = 1f;
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private static int fnv1a(String value) {
        int hash = 0x811C9DC5;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xFF);
            hash *= 0x01000193;
        }
        return hash;
    }
}
