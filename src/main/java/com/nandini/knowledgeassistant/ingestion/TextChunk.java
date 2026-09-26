package com.nandini.knowledgeassistant.ingestion;

/**
 * One chunk of a document. {@code charStart}/{@code charEnd} are offsets within the page text,
 * which makes it possible to highlight the exact evidence in the source.
 */
public record TextChunk(int index, String content, Integer pageNumber, int charStart, int charEnd) {

    /** Rough token estimate (about 4 characters per token for English). */
    public int tokenEstimate() {
        return Math.max(1, (content.length() + 3) / 4);
    }
}
