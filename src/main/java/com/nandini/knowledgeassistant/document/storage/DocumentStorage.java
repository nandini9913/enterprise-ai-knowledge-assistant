package com.nandini.knowledgeassistant.document.storage;

/**
 * Port for storing original document bytes. Implementations: {@link S3DocumentStorage}
 * (production) and {@link LocalDocumentStorage} (local development and tests).
 */
public interface DocumentStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
