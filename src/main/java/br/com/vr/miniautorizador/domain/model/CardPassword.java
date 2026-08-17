package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;

public record CardPassword(String value) {

    private static final int MAX_LENGTH = 72;

    public CardPassword {
        Objects.requireNonNull(value, "Card password is required");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Card password must contain between 1 and 72 characters");
        }
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
