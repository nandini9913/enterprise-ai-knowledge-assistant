package com.nandini.knowledgeassistant.retrieval;

import com.nandini.knowledgeassistant.ai.embedding.EmbeddingClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fails fast at startup if the configured embedding model produces vectors of a different
 * size than the database column. Without this check the mismatch would only surface as
 * failed inserts once the first document is processed.
 */
@Component
public class EmbeddingDimensionCheck implements ApplicationRunner {

    private final ChunkVectorRepository repository;
    private final EmbeddingClient embeddingClient;

    public EmbeddingDimensionCheck(ChunkVectorRepository repository, EmbeddingClient embeddingClient) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer declared = repository.declaredDimensions();
        if (declared != null && declared > 0 && declared != embeddingClient.dimensions()) {
            throw new IllegalStateException("Embedding model " + embeddingClient.modelId() + " produces "
                    + embeddingClient.dimensions() + " dimensions but document_chunks.embedding is vector("
                    + declared + "). Align app.ai.embedding.dimensions with the database migration.");
        }
    }
}
