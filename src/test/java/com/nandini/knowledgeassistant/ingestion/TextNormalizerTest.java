package com.nandini.knowledgeassistant.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void joinsVisualLineWrapsButKeepsParagraphs() {
        assertThat(normalizer.normalize("First line\nwraps here.\n\n\n\nNew paragraph."))
                .isEqualTo("First line wraps here.\n\nNew paragraph.");
    }

    @Test
    void repairsWordsHyphenatedAcrossLines() {
        assertThat(normalizer.normalize("The organi-\nzation grows.")).isEqualTo("The organization grows.");
    }

    @Test
    void collapsesWhitespaceAndRemovesControlCharacters() {
        assertThat(normalizer.normalize("  Tabs\tand\u00A0spaces \u0007here  ")).isEqualTo("Tabs and spaces here");
    }

    @Test
    void foldsLigaturesAndWindowsLineEndings() {
        assertThat(normalizer.normalize("e\uFB03cient\r\nprocess")).isEqualTo("efficient process");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("")).isEmpty();
    }
}
