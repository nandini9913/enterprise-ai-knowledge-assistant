package com.nandini.knowledgeassistant.ingestion;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans extracted text so that chunking and embeddings see consistent input.
 * Paragraph breaks (blank lines) are preserved because the chunker uses them as
 * preferred split points.
 */
@Component
public class TextNormalizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern HYPHENATED_LINE_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[\\t\\x0B\\f\\u00A0 ]+");
    private static final Pattern SPACE_AROUND_NEWLINE = Pattern.compile(" *\\n *");
    private static final Pattern SINGLE_NEWLINE = Pattern.compile("(?<!\\n)\\n(?!\\n)");
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");

    public String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // NFKC folds compatibility characters (ligatures such as "ﬁ", full-width forms).
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = HYPHENATED_LINE_BREAK.matcher(text).replaceAll("$1$2");
        text = HORIZONTAL_WHITESPACE.matcher(text).replaceAll(" ");
        text = SPACE_AROUND_NEWLINE.matcher(text).replaceAll("\n");
        // A single newline inside a paragraph is usually a visual line wrap, not a break.
        text = SINGLE_NEWLINE.matcher(text).replaceAll(" ");
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
