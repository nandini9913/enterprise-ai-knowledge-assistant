package com.nandini.knowledgeassistant.ai.chat;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calls a Bedrock foundation model through the Converse API.
 * <p>
 * Converse gives one request/response shape for every chat model on Bedrock, so changing
 * {@code app.ai.chat.bedrock-model-id} is enough to switch models. Retries with backoff and
 * timeouts are configured on the SDK client (see {@code AwsConfig}).
 */
@Component
@ConditionalOnProperty(name = "app.ai.chat.provider", havingValue = "bedrock")
public class BedrockChatModel implements ChatModel {

    private final BedrockRuntimeClient bedrock;
    private final String modelId;

    public BedrockChatModel(BedrockRuntimeClient bedrock, AiProperties properties) {
        this.bedrock = bedrock;
        this.modelId = properties.chat().bedrockModelId();
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalStateException("app.ai.chat.bedrock-model-id must be set for the bedrock provider");
        }
    }

    @Override
    public ChatResponse generate(ChatRequest request) {
        try {
            ConverseResponse response = bedrock.converse(converse -> converse
                    .modelId(modelId)
                    .system(SystemContentBlock.fromText(request.systemPrompt()))
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(request.userMessage()))
                            .build())
                    .inferenceConfig(config -> config.maxTokens(request.maxTokens())));

            String text = response.output().message().content().stream()
                    .map(ContentBlock::text)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining());
            TokenUsage usage = response.usage();
            return new ChatResponse(text, modelId,
                    usage == null ? 0 : usage.inputTokens(),
                    usage == null ? 0 : usage.outputTokens(),
                    response.stopReasonAsString());
        } catch (ThrottlingException ex) {
            throw new ApiException(ErrorCode.MODEL_THROTTLED,
                    "The language model is busy. Please retry shortly.", ex);
        } catch (SdkException ex) {
            throw new ApiException(ErrorCode.MODEL_UNAVAILABLE, "The language model is unavailable.", ex);
        }
    }

    @Override
    public String modelId() {
        return modelId;
    }
}
