package com.taskscheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

public final class Properties {
    private Properties() {}

    @ConfigurationProperties(prefix = "app.jwt")
    public record Jwt(String secret, long expirationMinutes) {}

    @ConfigurationProperties(prefix = "app.viacep")
    public record ViaCep(String baseUrl) {}

    @ConfigurationProperties(prefix = "app.limits")
    public record Limits(int authRequestsPerMinute, int maxBodyBytes) {}
}
