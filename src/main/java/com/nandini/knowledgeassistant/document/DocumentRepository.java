package com.nandini.knowledgeassistant.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByOwnerIdAndChecksumSha256(UUID ownerId, String checksumSha256);

    /** Documents the user owns or has been granted access to. */
    @Query("""
            select d from Document d
            where d.ownerId = :userId
               or exists (select 1 from DocumentAccess a where a.id.documentId = d.id and a.id.userId = :userId)
            """)
    Page<Document> findAccessibleBy(@Param("userId") UUID userId, Pageable pageable);

    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);

    /**
     * Atomically claims a document for processing. Succeeds only if the document is waiting
     * ({@code UPLOADED}) or a previous worker's lease has expired (it crashed mid-processing).
     * Because SQS delivers at least once, two workers can receive the same message; this
     * conditional update guarantees only one of them proceeds.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Document d
               set d.status = com.nandini.knowledgeassistant.document.DocumentStatus.PROCESSING,
                   d.processingAttempts = d.processingAttempts + 1,
                   d.updatedAt = :now,
                   d.version = d.version + 1
             where d.id = :id
               and (d.status = com.nandini.knowledgeassistant.document.DocumentStatus.UPLOADED
                    or (d.status = com.nandini.knowledgeassistant.document.DocumentStatus.PROCESSING
                        and d.updatedAt < :leaseExpiredBefore))
            """)
    int claimForProcessing(@Param("id") UUID id, @Param("now") Instant now,
                           @Param("leaseExpiredBefore") Instant leaseExpiredBefore);
}
