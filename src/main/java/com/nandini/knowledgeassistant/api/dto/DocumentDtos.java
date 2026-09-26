package com.nandini.knowledgeassistant.api.dto;

import com.nandini.knowledgeassistant.document.Document;
import com.nandini.knowledgeassistant.document.DocumentAccess;
import com.nandini.knowledgeassistant.document.DocumentChunk;
import com.nandini.knowledgeassistant.document.DocumentStatus;
import com.nandini.knowledgeassistant.document.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(
            UUID id,
            String fileName,
            DocumentType type,
            long sizeBytes,
            DocumentStatus status,
            String failureReason,
            int chunkCount,
            int processingAttempts,
            UUID ownerId,
            Instant createdAt,
            Instant updatedAt,
            Instant processedAt) {

        public static DocumentResponse from(Document d) {
            return new DocumentResponse(d.getId(), d.getFilename(), d.getType(), d.getSizeBytes(), d.getStatus(),
                    d.getFailureReason(), d.getChunkCount(), d.getProcessingAttempts(), d.getOwnerId(),
                    d.getCreatedAt(), d.getUpdatedAt(), d.getProcessedAt());
        }
    }

    public record ChunkResponse(UUID id, int chunkIndex, Integer page, int charStart, int charEnd, int tokenEstimate,
                                String content) {
        public static ChunkResponse from(DocumentChunk c) {
            return new ChunkResponse(c.getId(), c.getChunkIndex(), c.getPageNumber(), c.getCharStart(),
                    c.getCharEnd(), c.getTokenEstimate(), c.getContent());
        }
    }

    public record GrantAccessRequest(@NotBlank @Size(max = 50) String username) {
    }

    public record AccessResponse(UUID documentId, UUID userId, UUID grantedBy, Instant grantedAt) {
        public static AccessResponse from(DocumentAccess a) {
            return new AccessResponse(a.getDocumentId(), a.getUserId(), a.getGrantedBy(), a.getGrantedAt());
        }
    }
}
