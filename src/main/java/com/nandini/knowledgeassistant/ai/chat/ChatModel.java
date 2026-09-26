package com.nandini.knowledgeassistant.ai.chat;

/**
 * Provider-neutral LLM port. The RAG service depends only on this interface, so the model
 * (or provider) can change through configuration without touching business logic.
 */
public interface ChatModel {

    ChatResponse generate(ChatRequest request);

    String modelId();
}
