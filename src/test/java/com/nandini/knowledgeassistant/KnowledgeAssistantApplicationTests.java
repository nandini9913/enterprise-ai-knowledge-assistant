package com.nandini.knowledgeassistant;

import com.nandini.knowledgeassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeAssistantApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsAndMigrationsApply() {
        // Starting the context runs Flyway and Hibernate schema validation against pgvector.
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void actuatorHealthIsPublicButMetricsRequireAdmin() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void safeInboundRequestIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "trace-123"))
                .andExpect(header().string("X-Request-Id", "trace-123"));
    }
}
