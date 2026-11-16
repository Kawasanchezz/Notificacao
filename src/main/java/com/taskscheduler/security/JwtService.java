package com.taskscheduler.security;

import com.taskscheduler.config.Properties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
public class JwtService {

    private static final String ISSUER = "task-scheduler-bff";

    private final SecretKey key;
    private final Duration ttl;
    /** Length of a canonical signature segment; the JWT decoder tolerates trailing junk, so we check it. */
    private final int signatureLength;

    public JwtService(Properties.Jwt props) {
        String secret = props.secret() == null ? "" : props.secret();
        String lower = secret.toLowerCase(Locale.ROOT);
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32
                || lower.contains("change-me") || lower.contains("replace-with")) {
            throw new IllegalStateException("app.jwt.secret must be a random value of at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(props.expirationMinutes());
        String probe = generate(0L, "probe");
        this.signatureLength = probe.length() - probe.lastIndexOf('.') - 1;
    }

    public String generate(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() { return ttl.toSeconds(); }

    /** Returns the user id if the token is validly signed, issued by us and unexpired. */
    public Optional<Long> parseUserId(String token) {
        if (token == null || token.length() - token.lastIndexOf('.') - 1 != signatureLength) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
