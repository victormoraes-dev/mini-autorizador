package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;

public record CardNumber(String value) {

    private static final int MAX_LENGTH = 32;

    public CardNumber {
        Objects.requireNonNull(value, "Card number is required");
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Card number must contain between 1 and 32 characters");
        }
    }

    public String masked() {
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
