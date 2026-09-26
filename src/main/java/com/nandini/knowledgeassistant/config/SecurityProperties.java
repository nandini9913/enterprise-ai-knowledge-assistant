package com.nandini.knowledgeassistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Security settings. The JWT secret has no default anywhere in committed configuration
 * except the local/test profiles, so a production start without {@code JWT_SECRET} fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid BootstrapAdmin bootstrapAdmin) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits) for HS256") String secret,
            @NotBlank String issuer,
            @NotNull Duration ttl) {
    }

    /** Optional first administrator, created at startup when both values are provided. */
    public record BootstrapAdmin(String username, String password) {
        public boolean isConfigured() {
            return username != null && !username.isBlank() && password != null && !password.isBlank();
        }
    }
}
