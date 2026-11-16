package com.taskscheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window, per-client limiter for the public auth endpoints (brute force and signup spam).
 * The client is the TCP peer address; X-Forwarded-For is deliberately ignored because clients can forge it.
 * State is in memory, so with several instances each enforces its own window.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private record Window(long start, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimitFilter(int requestsPerMinute) { this.limit = requestsPerMinute; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
        }
        Window w = windows.merge(request.getRemoteAddr(), new Window(now, 1),
                (old, fresh) -> now - old.start() >= WINDOW_MILLIS ? fresh : new Window(old.start(), old.count() + 1));
        if (w.count() > limit) {
            long retryAfter = Math.max(1, (WINDOW_MILLIS - (now - w.start()) + 999) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            ProblemResponses.write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests, try again later");
            return;
        }
        chain.doFilter(request, response);
    }
}
