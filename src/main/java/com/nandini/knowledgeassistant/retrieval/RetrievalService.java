package com.nandini.knowledgeassistant.retrieval;

import com.nandini.knowledgeassistant.ai.embedding.EmbeddingClient;
import com.nandini.knowledgeassistant.ai.embedding.EmbeddingException;
import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/** Embeds the question and returns the most similar chunks the user is allowed to read. */
@Service
public class RetrievalService {

    private final EmbeddingClient embeddingClient;
    private final ChunkVectorRepository repository;
    private final Timer retrievalTimer;

    public RetrievalService(EmbeddingClient embeddingClient, ChunkVectorRepository repository, MeterRegistry meters) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.retrievalTimer = Timer.builder("rag.retrieval.latency")
                .description("Question embedding plus vector search")
                .register(meters);
    }

    public List<RetrievedChunk> retrieve(String question, AuthenticatedUser user, Collection<java.util.UUID> documentIds,
                                         int topK, double minSimilarity) {
        return retrievalTimer.record(() -> {
            float[] queryVector;
            try {
                queryVector = embeddingClient.embed(question);
            } catch (EmbeddingException ex) {
                throw new ApiException(ErrorCode.MODEL_UNAVAILABLE, "The embedding model is unavailable.", ex);
            }
            return repository.search(queryVector, user.id(), user.isAdmin(), documentIds, topK).stream()
                    .filter(chunk -> chunk.similarity() >= minSimilarity)
                    .toList();
        });
    }
}
