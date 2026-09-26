package com.nandini.knowledgeassistant.rag;

import com.nandini.knowledgeassistant.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and normalizes the raw model output:
 * detects the insufficient-context sentinel, maps {@code [S#]} markers to real chunks,
 * removes markers that reference sources that were never provided (hallucinated citations),
 * and enforces the maximum answer length.
 */
@Component
public class AnswerParser {

    private static final Pattern CITATION = Pattern.compile("\\[S(\\d+)]");

    public ParsedAnswer parse(String rawText, List<RetrievedChunk> sources, int maxAnswerChars) {
        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty() || text.startsWith(PromptBuilder.INSUFFICIENT_CONTEXT_SENTINEL)) {
            return new ParsedAnswer(null, List.of(), true, false);
        }
        Set<Integer> cited = new LinkedHashSet<>();
        StringBuilder cleaned = new StringBuilder();
        Matcher matcher = CITATION.matcher(text);
        while (matcher.find()) {
            int sourceNumber = Integer.parseInt(matcher.group(1));
            boolean valid = sourceNumber >= 1 && sourceNumber <= sources.size();
            if (valid) {
                cited.add(sourceNumber);
            }
            matcher.appendReplacement(cleaned, valid ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(cleaned);

        String answer = cleaned.toString().replaceAll(" {2,}", " ").strip();
        boolean truncated = answer.length() > maxAnswerChars;
        if (truncated) {
            answer = answer.substring(0, maxAnswerChars).strip() + "...";
        }
        List<RetrievedChunk> citedChunks = new ArrayList<>();
        cited.forEach(number -> citedChunks.add(sources.get(number - 1)));
        return new ParsedAnswer(answer, citedChunks, false, truncated);
    }

    /**
     * @param citedSources chunks the answer explicitly cites; empty means the model did not
     *                     ground its answer in any provided source
     */
    public record ParsedAnswer(String answer, List<RetrievedChunk> citedSources, boolean insufficientContext,
                               boolean truncated) {
    }
}
