package com.nandini.knowledgeassistant.ingestion;

import com.nandini.knowledgeassistant.config.ChunkingProperties;
import com.nandini.knowledgeassistant.ingestion.extraction.ExtractedPage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic sliding-window chunker.
 * <ul>
 *   <li>Chunks never span pages, so every chunk has one exact page citation.</li>
 *   <li>Each chunk ends at the best natural boundary found in the last 40% of the window:
 *       paragraph break, then sentence end, then whitespace.</li>
 *   <li>Consecutive chunks overlap by roughly {@code overlap} characters so facts near a
 *       boundary appear intact in at least one chunk.</li>
 *   <li>A short trailing remainder is merged into the previous chunk instead of producing a
 *       tiny, low-information chunk.</li>
 * </ul>
 * The same input always yields the same chunks, which keeps reprocessing idempotent.
 */
@Component
public class TextChunker {

    private final ChunkingProperties properties;

    public TextChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(List<ExtractedPage> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        for (ExtractedPage page : pages) {
            chunkPage(page, chunks);
        }
        return chunks;
    }

    private void chunkPage(ExtractedPage page, List<TextChunk> chunks) {
        String text = page.text();
        if (text == null || text.isBlank()) {
            return;
        }
        int target = properties.targetSize();
        int overlap = properties.overlap();
        int length = text.length();
        int start = skipWhitespace(text, 0);

        while (start < length) {
            int end;
            if (length - start <= target + properties.minChunkSize()) {
                end = length;
            } else {
                end = findBoundary(text, start, start + target);
            }
            String content = text.substring(start, end).strip();
            if (content.length() >= properties.minChunkSize() || chunks.isEmpty() || end == length) {
                if (!content.isEmpty()) {
                    chunks.add(new TextChunk(chunks.size(), content, page.pageNumber(), start, end));
                }
            }
            if (end >= length) {
                break;
            }
            int next = Math.max(end - overlap, start + 1);
            next = moveToWordStart(text, next, end);
            start = skipWhitespace(text, next);
        }
    }

    /** Best split position in (windowEnd - 40% of target, windowEnd]. */
    private int findBoundary(String text, int start, int windowEnd) {
        int searchFrom = start + (int) (properties.targetSize() * 0.6);
        int paragraph = text.lastIndexOf("\n\n", windowEnd);
        if (paragraph >= searchFrom) {
            return paragraph;
        }
        for (int i = windowEnd - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length()
                    && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = windowEnd; i >= searchFrom; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return windowEnd; // no boundary at all (e.g. one very long token): hard split
    }

    /** Moves forward to the start of the next word so overlaps do not begin mid-word. */
    private static int moveToWordStart(String text, int position, int limit) {
        if (position == 0 || Character.isWhitespace(text.charAt(position - 1))) {
            return position;
        }
        int i = position;
        while (i < limit && !Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i < limit ? i : position;
    }

    private static int skipWhitespace(String text, int position) {
        int i = position;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }
}
