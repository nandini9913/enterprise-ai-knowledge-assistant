package com.nandini.knowledgeassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Grants a user read access to a document owned by someone else. */
@Entity
@Table(name = "document_access")
public class DocumentAccess {

    @EmbeddedId
    private Key id;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected DocumentAccess() {
    }

    public DocumentAccess(UUID documentId, UUID userId, UUID grantedBy) {
        this.id = new Key(documentId, userId);
        this.grantedBy = grantedBy;
        this.grantedAt = Instant.now();
    }

    public UUID getDocumentId() { return id.documentId; }
    public UUID getUserId() { return id.userId; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getGrantedAt() { return grantedAt; }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "document_id")
        private UUID documentId;

        @Column(name = "user_id")
        private UUID userId;

        protected Key() {
        }

        public Key(UUID documentId, UUID userId) {
            this.documentId = documentId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && documentId.equals(key.documentId) && userId.equals(key.userId);
        }

        @Override
        public int hashCode() {
            return 31 * documentId.hashCode() + userId.hashCode();
        }
    }
}
