package br.com.vr.miniautorizador.infrastructure.security;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.api")
public record ApiSecurityProperties(
        String readerKey,
        String writerKey,
        int requestsPerWindow,
        int windowSeconds) {

    private static final int MINIMUM_KEY_LENGTH = 32;

    public ApiSecurityProperties {
        requireStrongKey(readerKey, "Reader API key");
        requireStrongKey(writerKey, "Writer API key");
        if (Objects.equals(readerKey, writerKey)) {
            throw new IllegalArgumentException("Reader and writer API keys must be different");
        }
        if (requestsPerWindow < 1) {
            throw new IllegalArgumentException("Rate limit must allow at least one request");
        }
        if (windowSeconds < 1 || windowSeconds > 3600) {
            throw new IllegalArgumentException("Rate limit window must be between 1 and 3600 seconds");
        }
    }

    private static void requireStrongKey(String value, String name) {
        if (value == null || value.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalArgumentException(name + " must contain at least 32 characters");
        }
    }
}
