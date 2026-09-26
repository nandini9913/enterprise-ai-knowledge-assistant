package com.nandini.knowledgeassistant.api;

import com.nandini.knowledgeassistant.api.dto.DocumentDtos.DocumentResponse;
import com.nandini.knowledgeassistant.api.dto.PageResponse;
import com.nandini.knowledgeassistant.common.audit.AuditAction;
import com.nandini.knowledgeassistant.common.audit.AuditEvent;
import com.nandini.knowledgeassistant.common.audit.AuditEventRepository;
import com.nandini.knowledgeassistant.document.DocumentService;
import com.nandini.knowledgeassistant.document.DocumentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational endpoints. Protected twice: the URL rule in SecurityConfig and method-level
 * {@code @PreAuthorize} (defence in depth if the URL mapping ever changes).
 */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration")
public class AdminController {

    private final AuditEventRepository auditEvents;
    private final DocumentService documentService;

    public AdminController(AuditEventRepository auditEvents, DocumentService documentService) {
        this.auditEvents = auditEvents;
        this.documentService = documentService;
    }

    @GetMapping("/audit-events")
    @Operation(summary = "Most recent audit events, optionally for one user")
    public PageResponse<AuditEventResponse> auditEvents(@RequestParam(required = false) UUID userId,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size);
        var result = userId == null
                ? auditEvents.findAllByOrderByCreatedAtDesc(pageable)
                : auditEvents.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result, AuditEventResponse::from);
    }

    @GetMapping("/documents")
    @Operation(summary = "Documents in a given processing status, e.g. FAILED")
    public PageResponse<DocumentResponse> documentsByStatus(@RequestParam DocumentStatus status,
                                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                                            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(documentService.listByStatus(status, pageable), DocumentResponse::from);
    }

    public record AuditEventResponse(UUID id, UUID userId, AuditAction action, String resourceType,
                                     String resourceId, String detail, String requestId, Instant createdAt) {
        static AuditEventResponse from(AuditEvent e) {
            return new AuditEventResponse(e.getId(), e.getUserId(), e.getAction(), e.getResourceType(),
                    e.getResourceId(), e.getDetail(), e.getRequestId(), e.getCreatedAt());
        }
    }
}
