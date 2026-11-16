package com.taskscheduler.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.taskscheduler.exception.Exceptions.ConflictException;
import com.taskscheduler.security.JwtService;
import com.taskscheduler.user.User;
import com.taskscheduler.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
@SecurityRequirements // public endpoints: no lock icon in Swagger
public class AuthController {

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {

        /** bcrypt only uses the first 72 bytes, so multi-byte passwords must be bounded in bytes. */
        @JsonIgnore
        @AssertTrue(message = "Password must be at most 72 bytes")
        boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("timing-equalizer");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sign up and receive a JWT")
    public TokenResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw new ConflictException("Email already registered");
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        try {
            users.save(user);
        } catch (DataIntegrityViolationException e) { // concurrent signup with same email
            throw new ConflictException("Email already registered");
        }
        return token(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase()).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) throw new BadCredentialsException("Invalid credentials");
        return token(user);
    }

    private TokenResponse token(User user) {
        return new TokenResponse(jwt.generate(user.getId(), user.getEmail()), "Bearer", jwt.expiresInSeconds());
    }
}
