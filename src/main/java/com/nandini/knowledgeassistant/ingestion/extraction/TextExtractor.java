package com.nandini.knowledgeassistant.ingestion.extraction;

import com.nandini.knowledgeassistant.document.DocumentType;

import java.util.List;

public interface TextExtractor {

    DocumentType supportedType();

    List<ExtractedPage> extract(byte[] content);
}
