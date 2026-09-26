package com.nandini.knowledgeassistant.common.audit;

import com.nandini.knowledgeassistant.common.RequestIds;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import com.nandini.knowledgeassistant.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Writes audit events in their own transaction (REQUIRES_NEW) so that a failed business
 * operation - for example a rejected login - is still audited, and an audit failure
 * never breaks the business operation.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL = 500;

    private final AuditEventRepository repository;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditEventRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Records an action performed by the currently authenticated user (if any). */
    public void record(AuditAction action, String resourceType, Object resourceId, String detail) {
        UUID userId = CurrentUser.optional().map(AuthenticatedUser::id).orElse(null);
        recordFor(userId, action, resourceType, resourceId, detail);
    }

    /** Records an action on behalf of an explicit user, e.g. from a background worker. */
    public void recordFor(UUID userId, AuditAction action, String resourceType, Object resourceId, String detail) {
        AuditEvent event = new AuditEvent(userId, action, resourceType,
                resourceId == null ? null : resourceId.toString(), truncate(detail), RequestIds.current());
        try {
            requiresNew.executeWithoutResult(status -> repository.save(event));
        } catch (RuntimeException ex) {
            log.warn("Failed to write audit event {} for resource {}", action, resourceId, ex);
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAIL) {
            return value;
        }
        return value.substring(0, MAX_DETAIL);
    }
}
