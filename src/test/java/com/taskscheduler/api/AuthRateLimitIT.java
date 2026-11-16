package com.taskscheduler.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.limits.auth-requests-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitIT {

    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions attempt() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}")
                .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; }));
    }

    @Test
    void repeatedLoginAttemptsFromOneClientGetTooManyRequests() throws Exception {
        for (int i = 0; i < 3; i++) attempt().andExpect(status().isUnauthorized());
        attempt().andExpect(status().isTooManyRequests());
    }
}
