package br.com.vr.miniautorizador.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.password")
public record PasswordSecurityProperties(
        String pepper,
        int iterations,
        int saltLength,
        int keyLength) {

    public PasswordSecurityProperties {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException("Password pepper must be configured");
        }
        if (iterations < 100_000) {
            throw new IllegalArgumentException("PBKDF2 iterations must be at least 100000");
        }
        if (saltLength < 16) {
            throw new IllegalArgumentException("Password salt must contain at least 16 bytes");
        }
        if (keyLength < 256) {
            throw new IllegalArgumentException("Password key must contain at least 256 bits");
        }
    }
}
