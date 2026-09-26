package com.nandini.knowledgeassistant.security;

import com.nandini.knowledgeassistant.config.SecurityProperties;
import com.nandini.knowledgeassistant.user.Role;
import com.nandini.knowledgeassistant.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-012345";
    private final Instant now = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void issuedTokenVerifiesToTheSameIdentity() {
        JwtService service = service(SECRET, Clock.fixed(now, ZoneOffset.UTC));
        User user = user(Role.ADMIN);

        JwtService.IssuedToken token = service.issue(user);

        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(service.verify(token.token())).hasValueSatisfying(principal -> {
            assertThat(principal.id()).isEqualTo(user.getId());
            assertThat(principal.username()).isEqualTo("jane");
            assertThat(principal.isAdmin()).isTrue();
        });
    }

    @Test
    void expiredTokensAreRejected() {
        String token = service(SECRET, Clock.fixed(now, ZoneOffset.UTC)).issue(user(Role.USER)).token();
        JwtService later = service(SECRET, Clock.fixed(now.plus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokensSignedWithAnotherKeyAreRejected() {
        String token = service("another-secret-another-secret-0123456789", Clock.systemUTC())
                .issue(user(Role.USER)).token();

        assertThat(service(SECRET, Clock.systemUTC()).verify(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(service(SECRET, Clock.systemUTC()).verify("not.a.jwt")).isEmpty();
    }

    private static JwtService service(String secret, Clock clock) {
        return new JwtService(new SecurityProperties(
                new SecurityProperties.Jwt(secret, "test-issuer", Duration.ofMinutes(30)), null), clock);
    }

    private static User user(Role role) {
        User user = new User("jane", null, "hash", role);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
