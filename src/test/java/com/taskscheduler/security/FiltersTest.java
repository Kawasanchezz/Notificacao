package com.taskscheduler.security;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltersTest {

    private MockHttpServletResponse call(Filter filter, MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private MockHttpServletRequest post(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void rateLimitBlocksAfterLimitPerClientOnAuthPathsOnly() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3);
        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, post("/api/auth/login", "10.0.0.1")).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call(filter, post("/api/auth/login", "10.0.0.1"));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(call(filter, post("/api/auth/login", "10.0.0.2")).getStatus()).isEqualTo(200);
        assertThat(call(filter, post("/api/tasks", "10.0.0.1")).getStatus()).isEqualTo(200);
    }

    @Test
    void rateLimitIgnoresSpoofableForwardedForHeader() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1);
        MockHttpServletRequest first = post("/api/auth/login", "10.0.0.9");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        MockHttpServletRequest second = post("/api/auth/login", "10.0.0.9");
        second.addHeader("X-Forwarded-For", "2.2.2.2");
        assertThat(call(filter, first).getStatus()).isEqualTo(200);
        assertThat(call(filter, second).getStatus()).isEqualTo(429);
    }

    @Test
    void bodyLimitRejectsDeclaredAndStreamedOversizedBodies() throws Exception {
        BodySizeLimitFilter filter = new BodySizeLimitFilter(10);

        MockHttpServletRequest declared = post("/api/tasks", "10.0.0.1");
        declared.setContent(new byte[11]);
        declared.addHeader("Content-Length", "11");
        assertThat(call(filter, declared).getStatus()).isEqualTo(413);

        MockHttpServletRequest chunked = new MockHttpServletRequest("POST", "/api/tasks") {
            @Override
            public long getContentLengthLong() { return -1; } // chunked upload: length unknown up front
        };
        chunked.setContent(new byte[11]); // only the byte counter can catch this one
        MockFilterChain reader = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
                req.getInputStream().readAllBytes();
            }
        });
        assertThatThrownBy(() -> filter.doFilter(chunked, new MockHttpServletResponse(), reader))
                .isInstanceOf(IOException.class);

        MockHttpServletRequest ok = post("/api/tasks", "10.0.0.1");
        ok.setContent(new byte[10]);
        assertThat(call(filter, ok).getStatus()).isEqualTo(200);
    }

    @Test
    void cspIsSetForAppPagesButNotSwagger() throws Exception {
        CspFilter filter = new CspFilter();
        assertThat(call(filter, new MockHttpServletRequest("GET", "/")).getHeader("Content-Security-Policy"))
                .contains("default-src 'self'").contains("frame-ancestors 'none'");
        assertThat(call(filter, new MockHttpServletRequest("GET", "/swagger-ui/index.html"))
                .getHeader("Content-Security-Policy")).isNull();
    }
}
