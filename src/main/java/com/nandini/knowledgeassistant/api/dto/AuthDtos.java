package com.nandini.knowledgeassistant.api.dto;

import com.nandini.knowledgeassistant.user.Role;
import com.nandini.knowledgeassistant.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may contain letters, digits, '.', '_' and '-' only")
            String username,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 12, max = 128, message = "must be between 12 and 128 characters") String password) {

        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + "]"; // never log the password
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    public record UserResponse(UUID id, String username, String email, Role role, Instant createdAt) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                    user.getCreatedAt());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    }

    public record CurrentUserResponse(UUID id, String username, Role role) {
    }
}
