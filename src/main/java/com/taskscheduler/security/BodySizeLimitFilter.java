package com.taskscheduler.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Rejects oversized request bodies: by Content-Length up front, and by counting bytes for chunked uploads. */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private final int maxBytes;

    public BodySizeLimitFilter(int maxBytes) { this.maxBytes = maxBytes; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            ProblemResponses.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final int max;

        LimitedRequest(HttpServletRequest request, int max) { super(request); this.max = max; }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long total;

                @Override public int read() throws IOException { int b = in.read(); if (b >= 0) count(1); return b; }

                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0) count(n);
                    return n;
                }

                private void count(int n) throws IOException {
                    total += n;
                    if (total > max) throw new IOException("Request body too large");
                }

                @Override public boolean isFinished() { return in.isFinished(); }
                @Override public boolean isReady() { return in.isReady(); }
                @Override public void setReadListener(ReadListener l) { in.setReadListener(l); }
            };
        }
    }
}
