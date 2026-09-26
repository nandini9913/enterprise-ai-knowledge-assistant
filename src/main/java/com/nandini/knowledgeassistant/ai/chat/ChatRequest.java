package com.nandini.knowledgeassistant.ai.chat;

/** A single-turn generation request: fixed system instructions plus one user message. */
public record ChatRequest(String systemPrompt, String userMessage, int maxTokens) {
}
