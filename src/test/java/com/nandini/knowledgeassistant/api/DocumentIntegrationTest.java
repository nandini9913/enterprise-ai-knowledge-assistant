package com.nandini.knowledgeassistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.nandini.knowledgeassistant.support.IntegrationTest;
import com.nandini.knowledgeassistant.support.TestDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentIntegrationTest extends IntegrationTest {

    private static final byte[] HANDBOOK = TestDocuments.pdf(List.of(
            "Employee handbook. Remote work is permitted up to three days per week with manager approval.",
            "Security policy. Laptops must use full disk encryption and screens must lock after five minutes."));

    @Test
    void uploadedPdfIsProcessedIntoPagedChunks() throws Exception {
        TestUser owner = newUser("owner");

        JsonNode uploaded = readJson(upload(owner, "handbook.pdf", HANDBOOK)
                .andExpect(status().isAccepted())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.fileName").value("handbook.pdf"))
                .andExpect(jsonPath("$.type").value("PDF")));
        UUID id = UUID.fromString(uploaded.get("id").asText());

        // Test profile processes synchronously after commit, so the document is READY now.
        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.processingAttempts").value(1));

        mockMvc.perform(get("/api/v1/documents/{id}/chunks", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].page").value(1))
                .andExpect(jsonPath("$.items[1].page").value(2))
                .andExpect(jsonPath("$.items[1].content").value(org.hamcrest.Matchers.containsString("encryption")));
    }

    @Test
    void identicalUploadBySameUserIsRejectedAsDuplicate() throws Exception {
        TestUser owner = newUser("dup");
        byte[] pdf = TestDocuments.pdf(List.of("Unique content " + UUID.randomUUID()));
        uploadReady(owner, "a.pdf", pdf);

        upload(owner, "b.pdf", pdf)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_EXISTS"));
    }

    @Test
    void unsupportedAndSpoofedFilesAreRejected() throws Exception {
        TestUser owner = newUser("types");

        upload(owner, "notes.txt", "plain text".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value("Only PDF and DOCX documents are supported."));
        upload(owner, "fake.pdf", "this is not a pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_NOT_SUPPORTED"));
        upload(owner, "empty.pdf", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DOCUMENT_EMPTY"));
    }

    @Test
    void documentWithoutTextFailsPermanentlyWithAReason() throws Exception {
        TestUser owner = newUser("blank");
        UUID id = uploadReady(owner, "blank.pdf", TestDocuments.pdf(List.of("")));

        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value(org.hamcrest.Matchers.containsString("No extractable text")));
    }

    @Test
    void otherUsersCannotSeeOrDiscoverPrivateDocuments() throws Exception {
        TestUser owner = newUser("private");
        TestUser stranger = newUser("stranger");
        UUID id = uploadReady(owner, "private.pdf", TestDocuments.pdf(List.of("Private " + UUID.randomUUID())));

        // 404, not 403: a stranger must not learn that the id exists.
        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/documents/{id}/content", id).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, stranger.bearer()))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void sharedUsersCanReadButNotModify() throws Exception {
        TestUser owner = newUser("sharer");
        TestUser reader = newUser("reader");
        UUID id = uploadReady(owner, "shared.pdf", TestDocuments.pdf(List.of("Shared " + UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/documents/{id}/access", id)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", reader.username()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(reader.id().toString()));

        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(jsonPath("$.totalItems").value(1));
        mockMvc.perform(delete("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(delete("/api/v1/documents/{id}/access/{userId}", id, reader.id())
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, reader.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanDownloadReprocessAndDelete() throws Exception {
        TestUser owner = newUser("lifecycle");
        byte[] pdf = TestDocuments.pdf(List.of("Lifecycle " + UUID.randomUUID()));
        UUID id = uploadReady(owner, "life.pdf", pdf);

        byte[] downloaded = mockMvc.perform(get("/api/v1/documents/{id}/content", id)
                        .header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("life.pdf")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(pdf);

        mockMvc.perform(post("/api/v1/documents/{id}/reprocess", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isAccepted());
        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(1));

        mockMvc.perform(delete("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/documents/{id}", id).header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanListFailedDocuments() throws Exception {
        TestUser owner = newUser("failing");
        uploadReady(owner, "empty-text.pdf", TestDocuments.pdf(List.of(" ")));
        TestUser admin = login("test-admin", "test-admin-password");

        mockMvc.perform(get("/api/v1/admin/documents").param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.ownerId == '" + owner.id() + "')]").exists());
    }
}
