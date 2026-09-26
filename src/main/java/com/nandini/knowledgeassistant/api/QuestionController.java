package com.nandini.knowledgeassistant.api;

import com.nandini.knowledgeassistant.api.dto.QuestionDtos.QuestionRequest;
import com.nandini.knowledgeassistant.api.dto.QuestionDtos.QuestionResponse;
import com.nandini.knowledgeassistant.common.RequestIds;
import com.nandini.knowledgeassistant.rag.RagService;
import com.nandini.knowledgeassistant.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/questions")
@Tag(name = "Questions")
public class QuestionController {

    private final RagService ragService;

    public QuestionController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    @Operation(summary = "Ask a question answered from your documents, with citations")
    public QuestionResponse ask(@Valid @RequestBody QuestionRequest request) {
        var answer = ragService.answer(request.question(), request.documentIds(), request.topK(),
                CurrentUser.require());
        return QuestionResponse.from(answer, RequestIds.current());
    }
}
