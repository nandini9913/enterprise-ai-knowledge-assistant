package com.nandini.knowledgeassistant.ai.chat;

import com.nandini.knowledgeassistant.ai.embedding.LocalHashEmbeddingClient;
import com.nandini.knowledgeassistant.rag.PromptBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for an LLM, used for local development and tests.
 * <p>
 * It reads the sources and question from the prompt (the same prompt a real model gets),
 * picks the sentences with the most word overlap with the question, and returns them with
 * {@code [S#]} citation markers. When nothing overlaps it answers with the
 * insufficient-context sentinel, just as the system prompt instructs a real model to.
 */
@Component
@ConditionalOnProperty(name = "app.ai.chat.provider", havingValue = "local", matchIfMissing = true)
public class LocalExtractiveChatModel implements ChatModel {

    private static final Pattern SOURCE = Pattern.compile(
            "<source id=\"(S\\d+)\"[^>]*>(.*?)</source>", Pattern.DOTALL);
    private static final Pattern QUESTION = Pattern.compile("<question>(.*?)</question>", Pattern.DOTALL);
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");
    private static final int MAX_SENTENCES = 3;

    @Override
    public ChatResponse generate(ChatRequest request) {
        String prompt = request.userMessage();
        Matcher questionMatcher = QUESTION.matcher(prompt);
        Set<String> questionTerms = questionMatcher.find() ? words(unescape(questionMatcher.group(1))) : Set.of();

        List<Candidate> candidates = new ArrayList<>();
        Matcher source = SOURCE.matcher(prompt);
        while (source.find()) {
            String sourceId = source.group(1);
            for (String sentence : SENTENCE_END.split(unescape(source.group(2)).strip())) {
                Set<String> overlap = new HashSet<>(words(sentence));
                overlap.retainAll(questionTerms);
                if (!overlap.isEmpty()) {
                    candidates.add(new Candidate(sourceId, sentence.strip(), overlap.size()));
                }
            }
        }
        String answer = candidates.isEmpty()
                ? PromptBuilder.INSUFFICIENT_CONTEXT_SENTINEL
                : candidates.stream()
                        .sorted(Comparator.comparingInt(Candidate::score).reversed())
                        .limit(MAX_SENTENCES)
                        .map(c -> c.sentence() + " [" + c.sourceId() + "]")
                        .reduce((a, b) -> a + " " + b)
                        .orElseThrow();
        return new ChatResponse(answer, modelId(), prompt.length() / 4, answer.length() / 4, "end_turn");
    }

    @Override
    public String modelId() {
        return "local-extractive";
    }

    private static Set<String> words(String text) {
        return new HashSet<>(LocalHashEmbeddingClient.terms(text));
    }

    private static String unescape(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private record Candidate(String sourceId, String sentence, int score) {
    }
}
