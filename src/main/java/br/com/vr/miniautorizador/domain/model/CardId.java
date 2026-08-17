package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;
import java.util.UUID;

public record CardId(UUID value) {

    public CardId {
        Objects.requireNonNull(value, "Card id is required");
    }

    public static CardId generate() {
        return new CardId(UUID.randomUUID());
    }

    public static CardId from(String value) {
        Objects.requireNonNull(value, "Card id is required");
        try {
            return new CardId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Card id must be a valid UUID", exception);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
