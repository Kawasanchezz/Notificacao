package com.taskscheduler.security;

import com.taskscheduler.config.Properties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-signing-key-0123456789-abcdefghijk";

    private JwtService service(String secret, long minutes) {
        return new JwtService(new Properties.Jwt(secret, minutes));
    }

    @Test
    void roundTripReturnsUserId() {
        JwtService jwt = service(SECRET, 5);
        assertThat(jwt.parseUserId(jwt.generate(42L, "a@b.c"))).contains(42L);
    }

    @Test
    void rejectsShortAndPlaceholderSecrets() {
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, 5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("replace-with-at-least-32-random-characters", 5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsGarbageTamperedExpiredAndForeignTokens() {
        JwtService jwt = service(SECRET, 5);
        String valid = jwt.generate(1L, "a@b.c");
        String tampered = valid.substring(0, valid.length() - 2) + (valid.endsWith("AA") ? "BB" : "AA");
        String foreignKey = Jwts.builder().issuer("task-scheduler-bff").subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-key-another-key-another-key-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
        String wrongIssuer = Jwts.builder().issuer("someone-else").subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        String unsigned = Jwts.builder().subject("1").compact();

        assertThat(jwt.parseUserId(valid + "x")).as("trailing junk after the signature").isEmpty();
        assertThat(jwt.parseUserId(valid + "=")).as("padding after the signature").isEmpty();
        assertThat(jwt.parseUserId("garbage")).isEmpty();
        assertThat(jwt.parseUserId("")).isEmpty();
        assertThat(jwt.parseUserId(tampered)).isEmpty();
        assertThat(jwt.parseUserId(foreignKey)).isEmpty();
        assertThat(jwt.parseUserId(wrongIssuer)).isEmpty();
        assertThat(jwt.parseUserId(unsigned)).isEmpty();
        JwtService instantlyExpired = service(SECRET, 0);
        assertThat(instantlyExpired.parseUserId(instantlyExpired.generate(1L, "a@b.c"))).isEmpty();
    }
}
