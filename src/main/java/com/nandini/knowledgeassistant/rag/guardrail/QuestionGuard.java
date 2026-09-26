package com.nandini.knowledgeassistant.rag.guardrail;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.RagProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * First line of defence, applied before any retrieval or model call.
 * <p>
 * The pattern list is intentionally small: it blocks the most common attempts to override
 * or extract the system instructions. It is not the main protection - that is the prompt
 * design (retrieved text is treated as data) plus output validation - but it cheaply
 * rejects obvious abuse and gives an auditable signal.
 */
@Component
public class QuestionGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all |any )?(the )?(previous|prior|above|earlier) (instructions|prompts|rules)"),
            Pattern.compile("disregard (all |any )?(the )?(previous|prior|above|system) (instructions|prompts|rules)"),
            Pattern.compile("(reveal|show|print|repeat|output) (me )?(your|the) (system prompt|system instructions|hidden instructions|initial prompt)"),
            Pattern.compile("you are no longer"),
            Pattern.compile("\\bjailbreak\\b"),
            Pattern.compile("</?(system|instructions|sources?|question)>"));

    private final RagProperties properties;

    public QuestionGuard(RagProperties properties) {
        this.properties = properties;
    }

    /** Returns the normalized question or throws {@link ErrorCode#QUESTION_REJECTED}. */
    public String check(String rawQuestion) {
        String question = normalize(rawQuestion);
        if (question.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Question must not be blank.");
        }
        if (question.length() > properties.maxQuestionLength()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Question must be at most " + properties.maxQuestionLength() + " characters.");
        }
        String lower = question.toLowerCase(java.util.Locale.ROOT);
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                throw new ApiException(ErrorCode.QUESTION_REJECTED,
                        "The question was rejected by the assistant's safety rules.");
            }
        }
        return question;
    }

    /** NFKC-normalizes, strips control characters and collapses whitespace. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        text = text.replaceAll("[\\p{Cc}\\p{Cf}]", " ");
        return text.replaceAll("\\s+", " ").strip();
    }
}
