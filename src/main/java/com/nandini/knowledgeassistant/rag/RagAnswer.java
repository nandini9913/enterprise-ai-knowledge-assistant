package com.nandini.knowledgeassistant.rag;

import com.nandini.knowledgeassistant.retrieval.RetrievedChunk;

import java.util.List;

public record RagAnswer(
        AnswerStatus status,
        String answer,
        List<RetrievedChunk> citations,
        int retrievedChunks,
        String model,
        boolean truncated,
        long latencyMs) {
}
