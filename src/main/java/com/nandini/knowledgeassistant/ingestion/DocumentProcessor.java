package com.nandini.knowledgeassistant.ingestion;

import com.nandini.knowledgeassistant.ai.embedding.EmbeddingClient;
import com.nandini.knowledgeassistant.common.audit.AuditAction;
import com.nandini.knowledgeassistant.common.audit.AuditService;
import com.nandini.knowledgeassistant.config.AiProperties;
import com.nandini.knowledgeassistant.config.ProcessingProperties;
import com.nandini.knowledgeassistant.document.Document;
import com.nandini.knowledgeassistant.document.DocumentRepository;
import com.nandini.knowledgeassistant.document.storage.DocumentStorage;
import com.nandini.knowledgeassistant.ingestion.extraction.ExtractedPage;
import com.nandini.knowledgeassistant.ingestion.extraction.TextExtractorRegistry;
import com.nandini.knowledgeassistant.retrieval.ChunkVectorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ingestion pipeline: claim -> load from storage -> extract -> normalize -> chunk ->
 * embed -> store chunks and vectors -> READY.
 * <p>
 * Idempotency and concurrency:
 * <ul>
 *   <li>A document is claimed with a conditional UPDATE, so duplicate deliveries of the same
 *       message cannot process it twice in parallel.</li>
 *   <li>Chunks are replaced (delete + insert) in the same transaction that marks the document
 *       READY, so a retry never leaves duplicate or partial chunks.</li>
 *   <li>Slow work (download, parsing, embedding calls) runs outside any database transaction.</li>
 * </ul>
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final TextExtractorRegistry extractors;
    private final TextNormalizer normalizer;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final ChunkVectorRepository chunkVectors;
    private final ProcessingProperties processingProperties;
    private final AiProperties aiProperties;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Timer processingTimer;

    public DocumentProcessor(DocumentRepository documents, DocumentStorage storage, TextExtractorRegistry extractors,
                             TextNormalizer normalizer, TextChunker chunker, EmbeddingClient embeddingClient,
                             ChunkVectorRepository chunkVectors, ProcessingProperties processingProperties,
                             AiProperties aiProperties, AuditService audit, PlatformTransactionManager txManager,
                             Clock clock, MeterRegistry meters) {
        this.documents = documents;
        this.storage = storage;
        this.extractors = extractors;
        this.normalizer = normalizer;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.chunkVectors = chunkVectors;
        this.processingProperties = processingProperties;
        this.aiProperties = aiProperties;
        this.audit = audit;
        this.clock = clock;
        this.meters = meters;
        this.transaction = new TransactionTemplate(txManager);
        // Always start a fresh transaction: this may run from an after-commit callback.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.processingTimer = Timer.builder("documents.processing.latency")
                .description("End-to-end document ingestion time")
                .register(meters);
    }

    /**
     * @param finalAttempt when true, a transient failure marks the document FAILED instead of
     *                     leaving it for redelivery
     */
    public ProcessingOutcome process(UUID documentId, boolean finalAttempt) {
        Document document = claim(documentId);
        if (document == null) {
            return count(ProcessingOutcome.SKIPPED);
        }
        Timer.Sample sample = Timer.start(meters);
        try {
            List<TextChunk> chunks = extractAndChunk(document);
            List<float[]> embeddings = embed(chunks);
            Instant now = clock.instant();
            transaction.executeWithoutResult(status -> {
                chunkVectors.replaceChunks(documentId, chunks, embeddings);
                documents.findById(documentId).ifPresent(doc -> doc.markReady(chunks.size(), now));
            });
            audit.recordFor(document.getOwnerId(), AuditAction.DOCUMENT_PROCESSED, "DOCUMENT", documentId,
                    "chunks=" + chunks.size() + ", model=" + embeddingClient.modelId());
            log.info("Processed document {} into {} chunks", documentId, chunks.size());
            return count(ProcessingOutcome.COMPLETED);
        } catch (NonRetryableProcessingException ex) {
            log.warn("Document {} failed permanently: {}", documentId, ex.getMessage());
            return fail(document, ex.getMessage());
        } catch (RuntimeException ex) {
            if (finalAttempt) {
                log.error("Document {} failed on its final attempt", documentId, ex);
                return fail(document, "Processing failed after retries: " + rootMessage(ex));
            }
            log.warn("Document {} failed on attempt {}; will retry", documentId,
                    document.getProcessingAttempts(), ex);
            transaction.executeWithoutResult(status -> documents.findById(documentId)
                    .ifPresent(doc -> doc.markRetryPending(rootMessage(ex))));
            return count(ProcessingOutcome.RETRY);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /** Marks a document FAILED, e.g. when its message is about to move to the dead-letter queue. */
    public void markFailed(UUID documentId, String reason) {
        transaction.executeWithoutResult(status -> documents.findById(documentId)
                .ifPresent(doc -> doc.markFailed(reason)));
    }

    private Document claim(UUID documentId) {
        Instant now = clock.instant();
        Instant leaseExpiredBefore = now.minus(processingProperties.leaseTimeout());
        return transaction.execute(status -> {
            int claimed = documents.claimForProcessing(documentId, now, leaseExpiredBefore);
            if (claimed == 0) {
                log.info("Document {} not claimable (missing, READY, FAILED or owned by another worker)",
                        documentId);
                return null;
            }
            return documents.findById(documentId).orElse(null);
        });
    }

    private List<TextChunk> extractAndChunk(Document document) {
        byte[] content = storage.get(document.getStorageKey());
        List<ExtractedPage> pages = extractors.forType(document.getType()).extract(content).stream()
                .map(page -> new ExtractedPage(page.pageNumber(), normalizer.normalize(page.text())))
                .toList();
        List<TextChunk> chunks = chunker.chunk(pages);
        if (chunks.isEmpty()) {
            throw new NonRetryableProcessingException(
                    "No extractable text found (scanned documents require OCR, which is not supported yet).");
        }
        return chunks;
    }

    private List<float[]> embed(List<TextChunk> chunks) {
        int batchSize = aiProperties.embedding().batchSize();
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int from = 0; from < chunks.size(); from += batchSize) {
            List<String> batch = chunks.subList(from, Math.min(from + batchSize, chunks.size())).stream()
                    .map(TextChunk::content)
                    .toList();
            embeddings.addAll(embeddingClient.embed(batch));
        }
        return embeddings;
    }

    private ProcessingOutcome fail(Document document, String reason) {
        markFailed(document.getId(), reason);
        audit.recordFor(document.getOwnerId(), AuditAction.DOCUMENT_PROCESSING_FAILED, "DOCUMENT",
                document.getId(), reason);
        return count(ProcessingOutcome.FAILED);
    }

    private ProcessingOutcome count(ProcessingOutcome outcome) {
        meters.counter("documents.processed", "outcome", outcome.name()).increment();
        return outcome;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
