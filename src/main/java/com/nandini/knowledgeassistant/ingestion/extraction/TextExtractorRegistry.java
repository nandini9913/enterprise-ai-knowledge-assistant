package com.nandini.knowledgeassistant.ingestion.extraction;

import com.nandini.knowledgeassistant.document.DocumentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Selects the extractor for a document type (strategy pattern, wired by Spring). */
@Component
public class TextExtractorRegistry {

    private final Map<DocumentType, TextExtractor> extractors = new EnumMap<>(DocumentType.class);

    public TextExtractorRegistry(List<TextExtractor> available) {
        available.forEach(extractor -> extractors.put(extractor.supportedType(), extractor));
    }

    public TextExtractor forType(DocumentType type) {
        TextExtractor extractor = extractors.get(type);
        if (extractor == null) {
            throw new IllegalStateException("No text extractor registered for " + type);
        }
        return extractor;
    }
}
