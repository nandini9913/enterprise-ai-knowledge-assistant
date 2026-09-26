package com.nandini.knowledgeassistant.rag;

import com.nandini.knowledgeassistant.ai.chat.ChatModel;
import com.nandini.knowledgeassistant.ai.chat.ChatResponse;
import com.nandini.knowledgeassistant.common.audit.AuditService;
import com.nandini.knowledgeassistant.config.AiProperties;
import com.nandini.knowledgeassistant.config.RagProperties;
import com.nandini.knowledgeassistant.rag.guardrail.QuestionGuard;
import com.nandini.knowledgeassistant.rag.guardrail.SensitiveDataRedactor;
import com.nandini.knowledgeassistant.retrieval.RetrievalService;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import com.nandini.knowledgeassistant.user.Role;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.nandini.knowledgeassistant.rag.PromptBuilderTest.chunk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final RagProperties properties = new RagProperties(5, 10, 0.2, 10_000, 1000, 4000, false, true);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "u", Role.USER);
    private final RagService service;

    RagServiceTest() {
        when(chatModel.modelId()).thenReturn("test-model");
        service = new RagService(new QuestionGuard(properties), retrieval,
                new PromptBuilder(properties, new SensitiveDataRedactor()), chatModel, new AnswerParser(), properties,
                new AiProperties(new AiProperties.Embedding(AiProperties.Provider.LOCAL, 8, null, 1),
                        new AiProperties.Chat(AiProperties.Provider.LOCAL, null, 256, Duration.ofSeconds(5), 1)),
                mock(AuditService.class), new SimpleMeterRegistry());
    }

    @Test
    void doesNotCallTheModelWhenNothingRelevantIsRetrieved() {
        when(retrieval.retrieve(any(), eq(user), any(), anyInt(), anyDouble())).thenReturn(List.of());

        RagAnswer answer = service.answer("What is the policy?", null, null, user);

        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT_CONTEXT);
        verify(chatModel, never()).generate(any());
    }

    @Test
    void withholdsAnswersThatCiteNoSource() {
        when(retrieval.retrieve(any(), eq(user), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk("a.pdf", 1, "Fact.")));
        when(chatModel.generate(any())).thenReturn(new ChatResponse("Made-up answer.", "test-model", 10, 3, "end_turn"));

        RagAnswer answer = service.answer("What is the policy?", null, null, user);

        assertThat(answer.status()).isEqualTo(AnswerStatus.INSUFFICIENT_CONTEXT);
        assertThat(answer.answer()).doesNotContain("Made-up");
    }

    @Test
    void returnsCitedChunksForGroundedAnswers() {
        var chunks = List.of(chunk("a.pdf", 1, "Fact A."), chunk("b.pdf", 2, "Fact B."));
        when(retrieval.retrieve(any(), eq(user), any(), anyInt(), anyDouble())).thenReturn(chunks);
        when(chatModel.generate(any())).thenReturn(new ChatResponse("B holds [S2].", "test-model", 10, 3, "end_turn"));

        RagAnswer answer = service.answer("What holds?", null, null, user);

        assertThat(answer.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(answer.citations()).containsExactly(chunks.get(1));
        assertThat(answer.model()).isEqualTo("test-model");
    }

    @Test
    void requestedTopKIsCappedByConfiguration() {
        when(retrieval.retrieve(any(), eq(user), any(), anyInt(), anyDouble())).thenReturn(List.of());

        service.answer("What is the policy?", null, 50, user);

        verify(retrieval).retrieve(any(), eq(user), any(), eq(10), anyDouble());
    }
}
