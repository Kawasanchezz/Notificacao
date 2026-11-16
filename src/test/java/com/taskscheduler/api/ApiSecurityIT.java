package com.taskscheduler.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.taskscheduler.viacep.ViaCepClient;
import com.taskscheduler.viacep.ViaCepClient.ViaCepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API tests against real PostgreSQL and MongoDB; only ViaCEP is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityIT {

    private static final String PASSWORD = "S3cure-passw0rd"; // gitleaks:allow (test fixture, not a credential)

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @MockitoBean ViaCepClient viaCep;

    @BeforeEach
    void stubViaCep() {
        when(viaCep.lookup(anyString())).thenAnswer(inv -> {
            String cep = ((String) inv.getArgument(0)).replaceAll("\\D", "");
            return new ViaCepResponse(cep, "Avenida Paulista", "", "Bela Vista", "Sao Paulo", "SP", null);
        });
    }

    private ResultActions send(String method, String path, String token, Object body) throws Exception {
        var req = request(HttpMethod.valueOf(method), path);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON)
               .content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return mvc.perform(req);
    }

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String res = body(send("POST", "/api/auth/register", null,
                Map.of("name", "Test User", "email", email, "password", PASSWORD)).andExpect(status().isCreated()));
        return json.readTree(res).get("accessToken").asString();
    }

    private String createTask(String token) throws Exception {
        String res = body(send("POST", "/api/tasks", token, Map.of("title", "Pay rent")).andExpect(status().isCreated()));
        return json.readTree(res).get("id").asString();
    }

    @Test
    void protectedEndpointsRejectMissingOrInvalidTokens() throws Exception {
        for (String path : new String[]{"/api/tasks", "/api/users/me", "/api/cep/01310100", "/api/users/me/phones"}) {
            send("GET", path, null, null).andExpect(status().isUnauthorized());
            send("GET", path, "not.a.jwt", null).andExpect(status().isUnauthorized());
        }
        String token = register();
        send("GET", "/api/tasks", token + "x", null).andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayPublicAndSendSecurityHeaders() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidatesInput() throws Exception {
        send("POST", "/api/auth/register", null, Map.of("name", "", "email", "bad", "password", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        String multibyte = "ç".repeat(40); // 40 chars but 80 bytes: bcrypt would silently truncate
        send("POST", "/api/auth/register", null,
                Map.of("name", "X", "email", "mb-" + UUID.randomUUID() + "@example.com", "password", multibyte))
                .andExpect(status().isBadRequest());
        send("POST", "/api/auth/register", null, "{not json").andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        send("POST", "/api/auth/register", null, Map.of("name", "A", "email", email, "password", PASSWORD))
                .andExpect(status().isCreated());
        send("POST", "/api/auth/register", null, Map.of("name", "A", "email", email.toUpperCase(), "password", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        send("POST", "/api/auth/register", null, Map.of("name", "A", "email", email, "password", PASSWORD))
                .andExpect(status().isCreated());
        send("POST", "/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        String wrongPassword = body(send("POST", "/api/auth/login", null,
                Map.of("email", email, "password", "wrong-password")).andExpect(status().isUnauthorized()));
        String unknownUser = body(send("POST", "/api/auth/login", null,
                Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "password", "wrong-password"))
                .andExpect(status().isUnauthorized()));
        assertThat(json.readTree(wrongPassword).get("detail")).isEqualTo(json.readTree(unknownUser).get("detail"));
    }

    @Test
    void usersCannotReadChangeOrDeleteEachOthersTasks() throws Exception {
        String alice = register(), bob = register();
        String id = createTask(alice);

        send("GET", "/api/tasks/" + id, bob, null).andExpect(status().isNotFound());
        send("PATCH", "/api/tasks/" + id + "/status", bob, Map.of("status", "DONE")).andExpect(status().isNotFound());
        send("DELETE", "/api/tasks/" + id, bob, null).andExpect(status().isNotFound());
        send("GET", "/api/tasks", bob, null).andExpect(jsonPath("$.totalItems").value(0));

        send("GET", "/api/tasks/" + id, alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void usersCannotTouchEachOthersAddressesAndPhones() throws Exception {
        String alice = register(), bob = register();
        JsonNode addr = json.readTree(body(send("POST", "/api/users/me/addresses", alice,
                Map.of("cep", "01310-100", "number", "10")).andExpect(status().isCreated())));
        JsonNode phone = json.readTree(body(send("POST", "/api/users/me/phones", alice,
                Map.of("number", "+5511999998888", "type", "MOBILE")).andExpect(status().isCreated())));

        send("DELETE", "/api/users/me/addresses/" + addr.get("id").asLong(), bob, null).andExpect(status().isNotFound());
        send("PUT", "/api/users/me/phones/" + phone.get("id").asLong(), bob,
                Map.of("number", "+5511888887777", "type", "HOME")).andExpect(status().isNotFound());
        send("GET", "/api/users/me", alice, null).andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.phones[0].number").value("+5511999998888"));
    }

    @Test
    void profileResponseNeverExposesPasswordHash() throws Exception {
        String res = body(send("GET", "/api/users/me", register(), null).andExpect(status().isOk()));
        assertThat(res).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    void inputIsValidatedAndBounded() throws Exception {
        String token = register();
        send("POST", "/api/tasks", token, Map.of("title", "")).andExpect(status().isBadRequest());
        send("POST", "/api/tasks", token, Map.of("title", "x".repeat(201))).andExpect(status().isBadRequest());
        String id = createTask(token);
        send("PATCH", "/api/tasks/" + id + "/status", token, Map.of("status", "HACKED")).andExpect(status().isBadRequest());
        send("GET", "/api/tasks?size=100000", token, null).andExpect(status().isBadRequest());
        send("GET", "/api/tasks?status=NOPE", token, null).andExpect(status().isBadRequest());
        send("POST", "/api/users/me/phones", token, Map.of("number", "abc", "type", "MOBILE")).andExpect(status().isBadRequest());
        send("POST", "/api/users/me/addresses", token, Map.of("cep", "123", "number", "1")).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedBodiesAreRefused() throws Exception {
        String token = register();
        send("POST", "/api/tasks", token, "{\"title\":\"" + "a".repeat(100_000) + "\"}")
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void perUserCollectionsAreCapped() throws Exception {
        String token = register();
        for (int i = 0; i < 10; i++) {
            send("POST", "/api/users/me/phones", token,
                    Map.of("number", "+55119999900" + String.format("%02d", i), "type", "MOBILE"))
                    .andExpect(status().isCreated());
        }
        send("POST", "/api/users/me/phones", token, Map.of("number", "+5511999990099", "type", "MOBILE"))
                .andExpect(status().isConflict());
    }

    @Test
    void taskLifecycle() throws Exception {
        String token = register();
        String id = createTask(token);
        send("PATCH", "/api/tasks/" + id + "/status", token, Map.of("status", "DONE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        send("GET", "/api/tasks?status=DONE", token, null).andExpect(jsonPath("$.totalItems").value(1));
        send("GET", "/api/tasks?status=PENDING", token, null).andExpect(jsonPath("$.totalItems").value(0));
        send("DELETE", "/api/tasks/" + id, token, null).andExpect(status().isNoContent());
        send("GET", "/api/tasks/" + id, token, null).andExpect(status().isNotFound());
    }
}
