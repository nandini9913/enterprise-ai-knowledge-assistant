package com.nandini.knowledgeassistant.retrieval;

import java.util.UUID;

/** A chunk returned by similarity search, with the metadata needed for citations. */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        String fileName,
        int chunkIndex,
        Integer pageNumber,
        String content,
        double similarity) {
}
