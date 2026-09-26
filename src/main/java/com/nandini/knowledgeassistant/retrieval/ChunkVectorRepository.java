package com.nandini.knowledgeassistant.retrieval;

import com.nandini.knowledgeassistant.ingestion.TextChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * All pgvector-specific SQL lives here.
 * <p>
 * Search uses the cosine distance operator {@code <=>} (0 = same direction, 2 = opposite)
 * and reports {@code similarity = 1 - distance}. Ordering by the raw operator expression lets
 * PostgreSQL use the HNSW index. Authorization is part of the same query, so chunks from
 * documents the user may not read can never be returned - not even to the prompt.
 */
@Repository
public class ChunkVectorRepository {

    private static final String SEARCH_SQL = """
            SELECT c.id, c.document_id, d.filename, c.chunk_index, c.page_number, c.content,
                   1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity
              FROM document_chunks c
              JOIN documents d ON d.id = c.document_id
             WHERE d.status = 'READY'
               AND (:isAdmin
                    OR d.owner_id = :userId
                    OR EXISTS (SELECT 1 FROM document_access a
                                WHERE a.document_id = d.id AND a.user_id = :userId))
               %s
             ORDER BY c.embedding <=> CAST(:embedding AS vector)
             LIMIT :limit
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public ChunkVectorRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    /** Replaces all chunks of a document. Delete-then-insert makes reprocessing idempotent. */
    public void replaceChunks(UUID documentId, List<TextChunk> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Each chunk needs exactly one embedding");
        }
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            rows.add(new Object[]{UUID.randomUUID(), documentId, chunk.index(), chunk.content(), chunk.pageNumber(),
                    chunk.charStart(), chunk.charEnd(), chunk.tokenEstimate(), VectorLiteral.of(embeddings.get(i)),
                    now});
        }
        jdbc.batchUpdate("""
                INSERT INTO document_chunks
                    (id, document_id, chunk_index, content, page_number, char_start, char_end,
                     token_estimate, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
                """, rows);
    }

    public List<RetrievedChunk> search(float[] queryEmbedding, UUID userId, boolean isAdmin,
                                       Collection<UUID> documentIds, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("embedding", VectorLiteral.of(queryEmbedding))
                .addValue("userId", userId)
                .addValue("isAdmin", isAdmin)
                .addValue("limit", limit);
        String documentFilter = "";
        if (documentIds != null && !documentIds.isEmpty()) {
            documentFilter = "AND d.id IN (:documentIds)";
            params.addValue("documentIds", documentIds);
        }
        return namedJdbc.query(SEARCH_SQL.formatted(documentFilter), params, (rs, rowNum) -> new RetrievedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("filename"),
                rs.getInt("chunk_index"),
                (Integer) rs.getObject("page_number"),
                rs.getString("content"),
                rs.getDouble("similarity")));
    }

    /** Dimension declared on the embedding column, e.g. 1024 for {@code vector(1024)}. */
    public Integer declaredDimensions() {
        return jdbc.queryForObject("""
                SELECT atttypmod FROM pg_attribute
                 WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'
                """, Integer.class);
    }
}
