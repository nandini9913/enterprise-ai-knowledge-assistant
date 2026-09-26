package com.nandini.knowledgeassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a stored chunk. The {@code embedding} column is intentionally not mapped:
 * vectors are written and searched with explicit SQL in
 * {@link com.nandini.knowledgeassistant.retrieval.ChunkVectorRepository}, which keeps the
 * pgvector specifics in one place and avoids loading 1024 floats whenever a chunk is read.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "char_start", nullable = false)
    private int charStart;

    @Column(name = "char_end", nullable = false)
    private int charEnd;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentChunk() {
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public Integer getPageNumber() { return pageNumber; }
    public int getCharStart() { return charStart; }
    public int getCharEnd() { return charEnd; }
    public int getTokenEstimate() { return tokenEstimate; }
    public Instant getCreatedAt() { return createdAt; }
}
