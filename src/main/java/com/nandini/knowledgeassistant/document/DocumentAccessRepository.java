package com.nandini.knowledgeassistant.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, DocumentAccess.Key> {

    boolean existsByIdDocumentIdAndIdUserId(UUID documentId, UUID userId);

    List<DocumentAccess> findByIdDocumentId(UUID documentId);
}
