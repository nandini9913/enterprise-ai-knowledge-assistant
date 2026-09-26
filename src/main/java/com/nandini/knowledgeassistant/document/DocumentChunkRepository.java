package com.nandini.knowledgeassistant.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    Page<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId, Pageable pageable);

    long countByDocumentId(UUID documentId);
}
