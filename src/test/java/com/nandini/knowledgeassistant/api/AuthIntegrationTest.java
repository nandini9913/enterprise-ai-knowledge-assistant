package com.nandini.knowledgeassistant.api;

import com.nandini.knowledgeassistant.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registeredUserCanLogInAndReadOwnIdentity() throws Exception {
        TestUser user = newUser("alice");

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(user.username()))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void duplicateUsernameIsRejectedCaseInsensitively() throws Exception {
        TestUser user = newUser("bob");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.username().toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void validationErrorsUseTheSharedErrorModel() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "x", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'username')]").exists());
    }

    @Test
    void malformedJsonIsReportedClearly() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongPasswordAndUnknownUserReturnTheSameError() throws Exception {
        TestUser user = newUser("carol");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", user.username(), "password", "wrong-password-123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody-here", "password", "wrong-password-123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        TestUser user = newUser("dave");
        String tampered = user.token().substring(0, user.token().length() - 4) + "abcd";
        mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUsersCannotReachAdminEndpoints() throws Exception {
        TestUser user = newUser("erin");

        mockMvc.perform(get("/api/v1/admin/audit-events").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void bootstrapAdminCanReadTheAuditTrail() throws Exception {
        newUser("frank");
        TestUser admin = login("test-admin", "test-admin-password");

        mockMvc.perform(get("/api/v1/admin/audit-events").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.action == 'USER_REGISTERED')]").exists());
    }
}
