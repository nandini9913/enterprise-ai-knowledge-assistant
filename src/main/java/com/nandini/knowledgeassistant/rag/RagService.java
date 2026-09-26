package com.nandini.knowledgeassistant.rag;

import com.nandini.knowledgeassistant.ai.chat.ChatModel;
import com.nandini.knowledgeassistant.ai.chat.ChatRequest;
import com.nandini.knowledgeassistant.ai.chat.ChatResponse;
import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.common.audit.AuditAction;
import com.nandini.knowledgeassistant.common.audit.AuditService;
import com.nandini.knowledgeassistant.config.AiProperties;
import com.nandini.knowledgeassistant.config.RagProperties;
import com.nandini.knowledgeassistant.rag.guardrail.QuestionGuard;
import com.nandini.knowledgeassistant.retrieval.RetrievalService;
import com.nandini.knowledgeassistant.retrieval.RetrievedChunk;
import com.nandini.knowledgeassistant.security.AuthenticatedUser;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The RAG question flow:
 * guard -> embed question -> authorized vector search -> grounded prompt -> LLM -> validate
 * output -> map citations.
 * <p>
 * The model is never called when retrieval finds nothing relevant: an explicit
 * "insufficient context" answer is cheaper and cannot hallucinate.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    static final String INSUFFICIENT_CONTEXT_MESSAGE =
            "I could not find enough information in the documents available to you to answer this question.";

    private final QuestionGuard guard;
    private final RetrievalService retrieval;
    private final PromptBuilder promptBuilder;
    private final ChatModel chatModel;
    private final AnswerParser answerParser;
    private final RagProperties ragProperties;
    private final AiProperties aiProperties;
    private final AuditService audit;
    private final MeterRegistry meters;
    private final Timer llmTimer;

    public RagService(QuestionGuard guard, RetrievalService retrieval, PromptBuilder promptBuilder,
                      ChatModel chatModel, AnswerParser answerParser, RagProperties ragProperties,
                      AiProperties aiProperties, AuditService audit, MeterRegistry meters) {
        this.guard = guard;
        this.retrieval = retrieval;
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel;
        this.answerParser = answerParser;
        this.ragProperties = ragProperties;
        this.aiProperties = aiProperties;
        this.audit = audit;
        this.meters = meters;
        this.llmTimer = Timer.builder("rag.llm.latency")
                .description("Language model generation latency")
                .tag("model", chatModel.modelId())
                .register(meters);
    }

    public RagAnswer answer(String rawQuestion, Collection<UUID> documentIds, Integer requestedTopK,
                            AuthenticatedUser user) {
        long started = System.nanoTime();
        String question;
        try {
            question = guard.check(rawQuestion);
        } catch (ApiException ex) {
            if (ex.code() == ErrorCode.QUESTION_REJECTED) {
                audit.record(AuditAction.QUESTION_REJECTED, "QUESTION", null, "guardrail=injection-pattern");
                meters.counter("rag.answers", "status", "REJECTED").increment();
            }
            throw ex;
        }
        int topK = Math.min(requestedTopK == null ? ragProperties.topK() : requestedTopK, ragProperties.maxTopK());

        List<RetrievedChunk> chunks = retrieval.retrieve(question, user, documentIds, topK,
                ragProperties.minSimilarity());
        if (chunks.isEmpty()) {
            return finish(insufficient(0, null, started), question);
        }

        PromptBuilder.Prompt prompt = promptBuilder.build(question, chunks);
        ChatResponse response = llmTimer.record(() -> chatModel.generate(
                new ChatRequest(prompt.system(), prompt.userMessage(), aiProperties.chat().maxTokens())));
        recordTokens(response);

        AnswerParser.ParsedAnswer parsed = answerParser.parse(response.text(), prompt.sources(),
                ragProperties.maxAnswerChars());
        if (parsed.insufficientContext()) {
            return finish(insufficient(chunks.size(), response.modelId(), started), question);
        }
        if (parsed.citedSources().isEmpty() && ragProperties.requireCitations()) {
            log.warn("Model {} returned an answer without citations; withholding it", response.modelId());
            return finish(insufficient(chunks.size(), response.modelId(), started), question);
        }
        List<RetrievedChunk> citations = parsed.citedSources().isEmpty() ? prompt.sources() : parsed.citedSources();
        return finish(new RagAnswer(AnswerStatus.ANSWERED, parsed.answer(), citations, chunks.size(),
                response.modelId(), parsed.truncated(), elapsedMs(started)), question);
    }

    private RagAnswer finish(RagAnswer answer, String question) {
        meters.counter("rag.answers", "status", answer.status().name()).increment();
        // Audit metadata only: the question text itself may contain sensitive information.
        audit.record(AuditAction.QUESTION_ANSWERED, "QUESTION", null,
                "status=" + answer.status() + ", questionChars=" + question.length()
                        + ", retrieved=" + answer.retrievedChunks() + ", cited=" + answer.citations().size());
        return answer;
    }

    private void recordTokens(ChatResponse response) {
        meters.counter("rag.llm.tokens", "model", response.modelId(), "type", "input")
                .increment(response.inputTokens());
        meters.counter("rag.llm.tokens", "model", response.modelId(), "type", "output")
                .increment(response.outputTokens());
        if ("max_tokens".equals(response.stopReason())) {
            log.warn("Model output hit max_tokens ({}); answer may be incomplete", aiProperties.chat().maxTokens());
        }
    }

    private static RagAnswer insufficient(int retrieved, String model, long started) {
        return new RagAnswer(AnswerStatus.INSUFFICIENT_CONTEXT, INSUFFICIENT_CONTEXT_MESSAGE, List.of(), retrieved,
                model, false, elapsedMs(started));
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
