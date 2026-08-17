package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;

public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "Password hash is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
