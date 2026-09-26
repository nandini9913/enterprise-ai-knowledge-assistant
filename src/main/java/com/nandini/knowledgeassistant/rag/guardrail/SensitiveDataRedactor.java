package com.nandini.knowledgeassistant.rag.guardrail;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Masks common personal data patterns in retrieved text before it is sent to the model.
 * Deliberately conservative (few false positives); a production deployment would pair this
 * with a dedicated PII service such as Amazon Comprehend or Bedrock Guardrails.
 */
@Component
public class SensitiveDataRedactor {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern CARD_NUMBER = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern US_SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    public String redact(String text) {
        String result = EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
        result = US_SSN.matcher(result).replaceAll("[REDACTED_ID]");
        return CARD_NUMBER.matcher(result).replaceAll("[REDACTED_NUMBER]");
    }
}
