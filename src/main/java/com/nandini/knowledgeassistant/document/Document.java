package com.nandini.knowledgeassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for an uploaded document. The original bytes live in object storage
 * ({@link #storageKey}); the searchable text lives in {@code document_chunks}.
 * <p>
 * The id is assigned by the application (not the database) so the storage key can be
 * derived from it before the row is inserted. Implementing {@link Persistable} tells
 * Spring Data that such an entity is new, so {@code save} issues an INSERT instead of a merge.
 */
@Entity
@Table(name = "documents")
public class Document implements Persistable<UUID> {

    private static final int MAX_FAILURE_REASON = 1000;

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 10)
    private DocumentType type;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    private long version;

    @Transient
    private boolean isNew = true;

    protected Document() {
    }

    public Document(UUID id, UUID ownerId, String filename, DocumentType type, String contentType,
                    long sizeBytes, String checksumSha256, String storageKey) {
        this.id = id;
        this.ownerId = ownerId;
        this.filename = filename;
        this.type = type;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.storageKey = storageKey;
        this.status = DocumentStatus.UPLOADED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markReady(int chunks, Instant at) {
        status = DocumentStatus.READY;
        chunkCount = chunks;
        processedAt = at;
        failureReason = null;
    }

    public void markFailed(String reason) {
        status = DocumentStatus.FAILED;
        failureReason = truncate(reason);
    }

    public void markRetryPending(String reason) {
        status = DocumentStatus.UPLOADED;
        failureReason = truncate(reason);
    }

    public void resetForReprocessing() {
        status = DocumentStatus.UPLOADED;
        failureReason = null;
        processingAttempts = 0;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON);
    }

    @Override
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getFilename() { return filename; }
    public DocumentType getType() { return type; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public String getStorageKey() { return storageKey; }
    public DocumentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public int getChunkCount() { return chunkCount; }
    public int getProcessingAttempts() { return processingAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getProcessedAt() { return processedAt; }
}
