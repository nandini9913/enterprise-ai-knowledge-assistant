package com.nandini.knowledgeassistant.api.dto;

import com.nandini.knowledgeassistant.rag.AnswerStatus;
import com.nandini.knowledgeassistant.rag.RagAnswer;
import com.nandini.knowledgeassistant.retrieval.RetrievedChunk;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class QuestionDtos {

    private static final int EXCERPT_LENGTH = 300;

    private QuestionDtos() {
    }

    public record QuestionRequest(
            @NotBlank @Size(max = 2000) String question,
            @Size(max = 50) List<UUID> documentIds,
            @Min(1) @Max(20) Integer topK) {
    }

    public record Citation(UUID documentId, String fileName, Integer page, UUID chunkId, int chunkIndex,
                           double score, String excerpt) {
        static Citation from(RetrievedChunk chunk) {
            String excerpt = chunk.content().length() <= EXCERPT_LENGTH
                    ? chunk.content()
                    : chunk.content().substring(0, EXCERPT_LENGTH) + "...";
            return new Citation(chunk.documentId(), chunk.fileName(), chunk.pageNumber(), chunk.chunkId(),
                    chunk.chunkIndex(), Math.round(chunk.similarity() * 1000) / 1000.0, excerpt);
        }
    }

    public record ResponseMetadata(String requestId, String model, int retrievedChunks, boolean truncated,
                                   long latencyMs) {
    }

    public record QuestionResponse(AnswerStatus status, String answer, List<Citation> citations,
                                   ResponseMetadata metadata) {
        public static QuestionResponse from(RagAnswer answer, String requestId) {
            return new QuestionResponse(answer.status(), answer.answer(),
                    answer.citations().stream().map(Citation::from).toList(),
                    new ResponseMetadata(requestId, answer.model(), answer.retrievedChunks(), answer.truncated(),
                            answer.latencyMs()));
        }
    }
}
