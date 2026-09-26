package com.nandini.knowledgeassistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base class for full-stack tests: real Spring context, real PostgreSQL + pgvector, MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Registers a user with a unique name and returns a bearer token for it. */
    protected TestUser newUser(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return login(username, PASSWORD);
    }

    protected TestUser login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return new TestUser(UUID.fromString(node.at("/user/id").asText()), username,
                node.get("accessToken").asText());
    }

    protected ResultActions upload(TestUser user, String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/v1/documents")
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .header(HttpHeaders.AUTHORIZATION, user.bearer()));
    }

    protected UUID uploadReady(TestUser user, String filename, byte[] content) throws Exception {
        String body = upload(user, filename, content)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    public record TestUser(UUID id, String username, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }
}
