package com.nandini.knowledgeassistant.api;

import com.nandini.knowledgeassistant.api.dto.DocumentDtos.AccessResponse;
import com.nandini.knowledgeassistant.api.dto.DocumentDtos.ChunkResponse;
import com.nandini.knowledgeassistant.api.dto.DocumentDtos.DocumentResponse;
import com.nandini.knowledgeassistant.api.dto.DocumentDtos.GrantAccessRequest;
import com.nandini.knowledgeassistant.api.dto.PageResponse;
import com.nandini.knowledgeassistant.document.Document;
import com.nandini.knowledgeassistant.document.DocumentService;
import com.nandini.knowledgeassistant.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Returns 202 Accepted: the file is stored, but it becomes searchable only once
     * asynchronous processing reaches READY. Clients poll {@code GET /documents/{id}}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF or DOCX document for ingestion")
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        Document document = documentService.upload(file, CurrentUser.require());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(document.getId()).toUri();
        return ResponseEntity.accepted().location(location).body(DocumentResponse.from(document));
    }

    @GetMapping
    @Operation(summary = "List documents you own or that were shared with you")
    public PageResponse<DocumentResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(documentService.listAccessible(CurrentUser.require(), pageable),
                DocumentResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document metadata and processing status")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.getReadable(id, CurrentUser.require()));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download the original file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DocumentService.DocumentContent content = documentService.download(id, CurrentUser.require());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.content());
    }

    @GetMapping("/{id}/chunks")
    @Operation(summary = "Inspect the stored chunks of a document")
    public PageResponse<ChunkResponse> chunks(@PathVariable UUID id,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(documentService.listChunks(id, CurrentUser.require(), PageRequest.of(page, size)),
                ChunkResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document, its chunks and its stored file")
    public void delete(@PathVariable UUID id) {
        documentService.delete(id, CurrentUser.require());
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "Re-run extraction, chunking and embedding")
    public ResponseEntity<DocumentResponse> reprocess(@PathVariable UUID id) {
        Document document = documentService.requestReprocessing(id, CurrentUser.require());
        return ResponseEntity.accepted().body(DocumentResponse.from(document));
    }

    @GetMapping("/{id}/access")
    @Operation(summary = "List users a document is shared with (owner/admin only)")
    public List<AccessResponse> listAccess(@PathVariable UUID id) {
        return documentService.listAccess(id, CurrentUser.require()).stream().map(AccessResponse::from).toList();
    }

    @PostMapping("/{id}/access")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Share a document with another user (read-only)")
    public AccessResponse grantAccess(@PathVariable UUID id, @Valid @RequestBody GrantAccessRequest request) {
        return AccessResponse.from(documentService.grantAccess(id, request.username(), CurrentUser.require()));
    }

    @DeleteMapping("/{id}/access/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a user's access to a document")
    public void revokeAccess(@PathVariable UUID id, @PathVariable UUID userId) {
        documentService.revokeAccess(id, userId, CurrentUser.require());
    }
}
