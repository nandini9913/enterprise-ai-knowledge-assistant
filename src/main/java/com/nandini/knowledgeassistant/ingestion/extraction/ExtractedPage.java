package com.nandini.knowledgeassistant.ingestion.extraction;

/**
 * Text of one logical page. {@code pageNumber} is 1-based for PDFs and {@code null}
 * for formats without fixed pagination (DOCX pagination depends on the renderer).
 */
public record ExtractedPage(Integer pageNumber, String text) {
}
