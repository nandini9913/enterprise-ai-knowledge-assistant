package com.nandini.knowledgeassistant.document;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.common.RequestIds;
import com.nandini.knowledgeassistant.common.audit.AuditAction;
import com.nandini.knowledgeassistant.common.audit.AuditService;
import com.nandini.knowledgeassistant.document.storage.DocumentStorage;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import com.nandini.knowledgeassistant.user.User;
import com.nandini.knowledgeassistant.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Document management use cases. Every read or write goes through an explicit
 * authorization check against the calling user.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documents;
    private final DocumentAccessRepository accessGrants;
    private final DocumentChunkRepository chunks;
    private final DocumentFileValidator validator;
    private final DocumentStorage storage;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents, DocumentAccessRepository accessGrants,
                           DocumentChunkRepository chunks, DocumentFileValidator validator, DocumentStorage storage, UserService userService,
                           ApplicationEventPublisher events, AuditService audit) {
        this.documents = documents;
        this.accessGrants = accessGrants;
        this.chunks = chunks;
        this.validator = validator;
        this.storage = storage;
        this.userService = userService;
        this.events = events;
        this.audit = audit;
    }

    /**
     * Validates and stores the file, records metadata as UPLOADED, and requests processing.
     * The processing request is only dispatched after the transaction commits, so a worker
     * can never pick up a document whose row does not exist yet.
     */
    @Transactional
    public Document upload(MultipartFile file, AuthenticatedUser user) {
        DocumentFileValidator.ValidatedFile validated = validator.validate(file);
        documents.findByOwnerIdAndChecksumSha256(user.id(), validated.checksumSha256()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.DOCUMENT_ALREADY_EXISTS,
                    "An identical document was already uploaded (id " + existing.getId() + ").");
        });

        UUID documentId = UUID.randomUUID();
        String storageKey = "documents/%s/%s.%s".formatted(user.id(), documentId, validated.type().extension());
        storage.put(storageKey, validated.content(), validated.type().mediaType());

        Document document;
        try {
            document = documents.saveAndFlush(new Document(documentId, user.id(), validated.filename(),
                    validated.type(), validated.type().mediaType(), validated.content().length,
                    validated.checksumSha256(), storageKey));
        } catch (RuntimeException ex) {
            // Keep storage and database consistent: remove the object if the row was not written.
            safeDelete(storageKey);
            if (ex instanceof DataIntegrityViolationException) {
                throw new ApiException(ErrorCode.DOCUMENT_ALREADY_EXISTS, "An identical document was already uploaded.", ex);
            }
            throw ex;
        }
        events.publishEvent(new DocumentEvents.ProcessingRequested(documentId, RequestIds.current()));
        audit.record(AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", documentId,
                "type=" + validated.type() + ", bytes=" + validated.content().length);
        return document;
    }

    @Transactional(readOnly = true)
    public Page<Document> listAccessible(AuthenticatedUser user, Pageable pageable) {
        return documents.findAccessibleBy(user.id(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Document> listByStatus(DocumentStatus status, Pageable pageable) {
        return documents.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Document getReadable(UUID documentId, AuthenticatedUser user) {
        Document document = find(documentId);
        if (!canRead(document, user)) {
            // 404 rather than 403: do not reveal that a document id exists.
            throw notFound();
        }
        return document;
    }

    @Transactional(readOnly = true)
    public DocumentContent download(UUID documentId, AuthenticatedUser user) {
        Document document = getReadable(documentId, user);
        byte[] content = storage.get(document.getStorageKey());
        audit.record(AuditAction.DOCUMENT_DOWNLOADED, "DOCUMENT", documentId, null);
        return new DocumentContent(document.getFilename(), document.getContentType(), content);
    }

    /** Stored chunks of a readable document - useful for inspecting chunking and retrieval quality. */
    @Transactional(readOnly = true)
    public Page<DocumentChunk> listChunks(UUID documentId, AuthenticatedUser user, Pageable pageable) {
        getReadable(documentId, user);
        return chunks.findByDocumentIdOrderByChunkIndex(documentId, pageable);
    }

    @Transactional
    public void delete(UUID documentId, AuthenticatedUser user) {
        Document document = getManageable(documentId, user);
        documents.delete(document);
        events.publishEvent(new DocumentEvents.Deleted(documentId, document.getStorageKey()));
        audit.record(AuditAction.DOCUMENT_DELETED, "DOCUMENT", documentId, null);
    }

    @Transactional
    public Document requestReprocessing(UUID documentId, AuthenticatedUser user) {
        Document document = getManageable(documentId, user);
        if (document.getStatus() == DocumentStatus.PROCESSING) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_READY, "Document is currently being processed.");
        }
        document.resetForReprocessing();
        events.publishEvent(new DocumentEvents.ProcessingRequested(documentId, RequestIds.current()));
        audit.record(AuditAction.DOCUMENT_REPROCESS_REQUESTED, "DOCUMENT", documentId, null);
        return document;
    }

    @Transactional
    public DocumentAccess grantAccess(UUID documentId, String username, AuthenticatedUser user) {
        Document document = getManageable(documentId, user);
        User grantee = userService.getByUsername(username);
        if (document.isOwnedBy(grantee.getId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The owner already has access to this document.");
        }
        DocumentAccess grant = accessGrants.findById(new DocumentAccess.Key(documentId, grantee.getId()))
                .orElseGet(() -> accessGrants.save(new DocumentAccess(documentId, grantee.getId(), user.id())));
        audit.record(AuditAction.DOCUMENT_ACCESS_GRANTED, "DOCUMENT", documentId, "grantee=" + grantee.getId());
        return grant;
    }

    @Transactional
    public void revokeAccess(UUID documentId, UUID granteeId, AuthenticatedUser user) {
        getManageable(documentId, user);
        accessGrants.deleteById(new DocumentAccess.Key(documentId, granteeId));
        audit.record(AuditAction.DOCUMENT_ACCESS_REVOKED, "DOCUMENT", documentId, "grantee=" + granteeId);
    }

    @Transactional(readOnly = true)
    public List<DocumentAccess> listAccess(UUID documentId, AuthenticatedUser user) {
        getManageable(documentId, user);
        return accessGrants.findByIdDocumentId(documentId);
    }

    /** Owners and admins may modify a document; users with a share grant may only read it. */
    private Document getManageable(UUID documentId, AuthenticatedUser user) {
        Document document = find(documentId);
        if (user.isAdmin() || document.isOwnedBy(user.id())) {
            return document;
        }
        if (accessGrants.existsByIdDocumentIdAndIdUserId(documentId, user.id())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only the owner can modify this document.");
        }
        throw notFound();
    }

    private boolean canRead(Document document, AuthenticatedUser user) {
        return user.isAdmin()
                || document.isOwnedBy(user.id())
                || accessGrants.existsByIdDocumentIdAndIdUserId(document.getId(), user.id());
    }

    private Document find(UUID documentId) {
        return documents.findById(documentId).orElseThrow(DocumentService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found.");
    }

    private void safeDelete(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to clean up stored object {}", storageKey, ex);
        }
    }

    public record DocumentContent(String filename, String contentType, byte[] content) {
    }
}
