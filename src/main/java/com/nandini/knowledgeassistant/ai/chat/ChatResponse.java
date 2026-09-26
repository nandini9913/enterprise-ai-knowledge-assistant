package com.nandini.knowledgeassistant.ai.chat;

public record ChatResponse(String text, String modelId, int inputTokens, int outputTokens, String stopReason) {
}
