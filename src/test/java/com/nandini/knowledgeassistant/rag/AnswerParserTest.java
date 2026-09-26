package com.nandini.knowledgeassistant.rag;

import com.nandini.knowledgeassistant.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.nandini.knowledgeassistant.rag.PromptBuilderTest.chunk;
import static org.assertj.core.api.Assertions.assertThat;

class AnswerParserTest {

    private final AnswerParser parser = new AnswerParser();
    private final List<RetrievedChunk> sources = List.of(chunk("a.pdf", 1, "A"), chunk("b.pdf", 2, "B"));

    @Test
    void mapsCitationMarkersToSourcesInOrderOfFirstUse() {
        AnswerParser.ParsedAnswer parsed = parser.parse("B says so [S2]. A agrees [S1][S2].", sources, 4000);

        assertThat(parsed.insufficientContext()).isFalse();
        assertThat(parsed.citedSources()).containsExactly(sources.get(1), sources.get(0));
    }

    @Test
    void removesCitationsToSourcesThatWereNeverProvided() {
        AnswerParser.ParsedAnswer parsed = parser.parse("Invented [S7] fact [S1].", sources, 4000);

        assertThat(parsed.answer()).isEqualTo("Invented fact [S1].");
        assertThat(parsed.citedSources()).containsExactly(sources.get(0));
    }

    @Test
    void recognisesTheInsufficientContextSentinel() {
        assertThat(parser.parse("INSUFFICIENT_CONTEXT", sources, 4000).insufficientContext()).isTrue();
        assertThat(parser.parse("  ", sources, 4000).insufficientContext()).isTrue();
    }

    @Test
    void reportsAnswersWithoutCitations() {
        assertThat(parser.parse("An unsupported claim.", sources, 4000).citedSources()).isEmpty();
    }

    @Test
    void truncatesOverlongAnswers() {
        AnswerParser.ParsedAnswer parsed = parser.parse("word ".repeat(100) + "[S1]", sources, 50);

        assertThat(parsed.truncated()).isTrue();
        assertThat(parsed.answer()).hasSizeLessThanOrEqualTo(53).endsWith("...");
    }
}
