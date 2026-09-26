package com.nandini.knowledgeassistant.ai.chat;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ValidationException;

import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockChatModelTest {

    private static final String MODEL = "anthropic.claude-opus-5";
    private final BedrockRuntimeClient bedrock = mock(BedrockRuntimeClient.class);
    private final BedrockChatModel model = new BedrockChatModel(bedrock, new AiProperties(
            new AiProperties.Embedding(AiProperties.Provider.LOCAL, 1024, null, 8),
            new AiProperties.Chat(AiProperties.Provider.BEDROCK, MODEL, 512, Duration.ofSeconds(30), 3)));

    @Test
    @SuppressWarnings("unchecked")
    void sendsSystemPromptAndUserMessageThroughConverse() {
        when(bedrock.converse(any(Consumer.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder().role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("The cap is 180 dollars [S1]."))
                        .build()))
                .usage(TokenUsage.builder().inputTokens(812).outputTokens(14).totalTokens(826).build())
                .stopReason(StopReason.END_TURN)
                .build());

        ChatResponse response = model.generate(new ChatRequest("system rules", "user message", 512));

        assertThat(response.text()).isEqualTo("The cap is 180 dollars [S1].");
        assertThat(response.inputTokens()).isEqualTo(812);
        assertThat(response.outputTokens()).isEqualTo(14);
        assertThat(response.stopReason()).isEqualTo("end_turn");

        ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(bedrock).converse(captor.capture());
        ConverseRequest.Builder builder = ConverseRequest.builder();
        captor.getValue().accept(builder);
        ConverseRequest request = builder.build();
        assertThat(request.modelId()).isEqualTo(MODEL);
        assertThat(request.system().get(0).text()).isEqualTo("system rules");
        assertThat(request.messages().get(0).content().get(0).text()).isEqualTo("user message");
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(512);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throttlingIsMappedToARetryableServiceUnavailableError() {
        when(bedrock.converse(any(Consumer.class))).thenThrow(ThrottlingException.builder().message("slow down").build());

        assertThatThrownBy(() -> model.generate(new ChatRequest("s", "u", 100)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).code())
                .isEqualTo(ErrorCode.MODEL_THROTTLED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void otherBedrockErrorsAreMappedWithoutLeakingDetails() {
        when(bedrock.converse(any(Consumer.class))).thenThrow(ValidationException.builder().message("bad model").build());

        assertThatThrownBy(() -> model.generate(new ChatRequest("s", "u", 100)))
                .isInstanceOf(ApiException.class)
                .hasMessage("The language model is unavailable.");
    }
}
