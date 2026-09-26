package com.nandini.knowledgeassistant.ingestion;

import com.nandini.knowledgeassistant.config.ChunkingProperties;
import com.nandini.knowledgeassistant.ingestion.extraction.ExtractedPage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(new ChunkingProperties(200, 40, 30));

    @Test
    void shortPageBecomesSingleChunkWithPageMetadata() {
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(3, "A short page of text.")));

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.index()).isZero();
            assertThat(chunk.pageNumber()).isEqualTo(3);
            assertThat(chunk.content()).isEqualTo("A short page of text.");
        });
    }

    @Test
    void longTextIsSplitAtSentenceBoundariesWithinTheTargetSize() {
        String text = sentences(40);
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(1, text)));

        assertThat(chunks).hasSizeGreaterThan(3);
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(200);
            assertThat(chunk.content()).endsWith(".");
        }
    }

    @Test
    void consecutiveChunksOverlap() {
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(1, sentences(40))));

        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).charStart()).isLessThan(chunks.get(i - 1).charEnd());
        }
    }

    @Test
    void chunksNeverSpanPagesAndIndexesAreSequential() {
        List<TextChunk> chunks = chunker.chunk(List.of(
                new ExtractedPage(1, sentences(10)), new ExtractedPage(2, sentences(10))));

        assertThat(chunks).extracting(TextChunk::index)
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).extracting(TextChunk::pageNumber).containsOnly(1, 2);
    }

    @Test
    void paragraphBreaksArePreferredSplitPoints() {
        String paragraph = "x".repeat(10) + " " + "word ".repeat(28);
        String text = paragraph.strip() + "\n\n" + "Second paragraph starts here. " + "more text ".repeat(20);

        TextChunk first = chunker.chunk(List.of(new ExtractedPage(1, text))).get(0);

        assertThat(first.content()).doesNotContain("Second paragraph");
    }

    @Test
    void shortTrailingRemainderIsMergedIntoThePreviousChunk() {
        String text = "word ".repeat(45) + "tail.";
        List<TextChunk> chunks = chunker.chunk(List.of(new ExtractedPage(1, text)));

        assertThat(chunks.get(chunks.size() - 1).content()).endsWith("tail.")
                .hasSizeGreaterThanOrEqualTo(30);
    }

    @Test
    void chunkingIsDeterministic() {
        List<ExtractedPage> pages = List.of(new ExtractedPage(1, sentences(30)));
        assertThat(chunker.chunk(pages)).isEqualTo(chunker.chunk(pages));
    }

    @Test
    void blankPagesProduceNoChunks() {
        assertThat(chunker.chunk(List.of(new ExtractedPage(1, "  "), new ExtractedPage(2, null)))).isEmpty();
    }

    private static String sentences(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "Sentence number " + i + " explains a detail of the policy.")
                .collect(Collectors.joining(" "));
    }
}
