package com.nandini.knowledgeassistant.retrieval;

import com.nandini.knowledgeassistant.ingestion.TextChunk;
import com.nandini.knowledgeassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the raw pgvector SQL: ordering by cosine distance, authorization and filters. */
class ChunkVectorRepositoryTest extends IntegrationTest {

    @Autowired
    private ChunkVectorRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsNearestChunksFirstAndOnlyFromReadableReadyDocuments() {
        UUID alice = insertUser("alice");
        UUID bob = insertUser("bob");
        UUID alicesDoc = insertDocument(alice, "READY");
        UUID bobsDoc = insertDocument(bob, "READY");
        UUID alicesPendingDoc = insertDocument(alice, "PROCESSING");

        repository.replaceChunks(alicesDoc,
                List.of(chunk(0, "exact match"), chunk(1, "close match"), chunk(2, "far away")),
                List.of(unit(0), mix(0, 1, 0.9f), unit(5)));
        repository.replaceChunks(bobsDoc, List.of(chunk(0, "bob's secret")), List.of(unit(0)));
        repository.replaceChunks(alicesPendingDoc, List.of(chunk(0, "not ready")), List.of(unit(0)));

        List<RetrievedChunk> results = repository.search(unit(0), alice, false, null, 10);

        assertThat(results).extracting(RetrievedChunk::content)
                .containsExactly("exact match", "close match", "far away");
        assertThat(results.get(0).similarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(results.get(2).similarity()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));

        assertThat(repository.search(unit(0), bob, false, null, 10))
                .extracting(RetrievedChunk::content).containsExactly("bob's secret");
        // Admins read everything (the database is shared with other tests, so filter to ours).
        assertThat(repository.search(unit(0), bob, true, List.of(alicesDoc, bobsDoc, alicesPendingDoc), 10))
                .hasSize(4);
    }

    @Test
    void sharedDocumentsAndDocumentFiltersAreRespected() {
        UUID owner = insertUser("owner");
        UUID reader = insertUser("reader");
        UUID shared = insertDocument(owner, "READY");
        UUID other = insertDocument(owner, "READY");
        repository.replaceChunks(shared, List.of(chunk(0, "shared text")), List.of(unit(1)));
        repository.replaceChunks(other, List.of(chunk(0, "other text")), List.of(unit(1)));
        jdbc.update("INSERT INTO document_access (document_id, user_id, granted_by) VALUES (?, ?, ?)",
                shared, reader, owner);

        assertThat(repository.search(unit(1), reader, false, null, 10))
                .extracting(RetrievedChunk::content).containsExactly("shared text");
        assertThat(repository.search(unit(1), owner, false, List.of(other), 10))
                .extracting(RetrievedChunk::documentId).containsOnly(other);
    }

    @Test
    void replacingChunksIsIdempotent() {
        UUID owner = insertUser("idem");
        UUID doc = insertDocument(owner, "READY");

        repository.replaceChunks(doc, List.of(chunk(0, "v1"), chunk(1, "v1b")), List.of(unit(0), unit(1)));
        repository.replaceChunks(doc, List.of(chunk(0, "v2")), List.of(unit(0)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id = ?",
                Integer.class, doc)).isEqualTo(1);
    }

    @Test
    void declaredColumnDimensionsMatchTheMigration() {
        assertThat(repository.declaredDimensions()).isEqualTo(1024);
    }

    private UUID insertUser(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, username, password_hash, role) VALUES (?, ?, 'x', 'USER')",
                id, name + "-" + id);
        return id;
    }

    private UUID insertDocument(UUID owner, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO documents (id, owner_id, filename, document_type, content_type, size_bytes,
                                       checksum_sha256, storage_key, status)
                VALUES (?, ?, 'f.pdf', 'PDF', 'application/pdf', 10, ?, ?, ?)
                """, id, owner, id.toString().replace("-", "") + "00000000000000000000000000000000", "k/" + id, status);
        return id;
    }

    private static TextChunk chunk(int index, String content) {
        return new TextChunk(index, content, 1, 0, content.length());
    }

    private static float[] unit(int axis) {
        float[] v = new float[1024];
        v[axis] = 1f;
        return v;
    }

    private static float[] mix(int a, int b, float weightA) {
        float[] v = new float[1024];
        v[a] = weightA;
        v[b] = (float) Math.sqrt(1 - weightA * weightA);
        return v;
    }
}
