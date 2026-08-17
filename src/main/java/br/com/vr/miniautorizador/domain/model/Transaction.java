package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;

public record Transaction(CardId cardId, CardPassword providedPassword, Money amount) {

    public Transaction {
        Objects.requireNonNull(cardId, "Card id is required");
        Objects.requireNonNull(providedPassword, "Provided password is required");
        Objects.requireNonNull(amount, "Transaction amount is required");
    }

    public static Transaction request(
            CardId cardId,
            CardPassword providedPassword,
            Money amount) {
        return new Transaction(cardId, providedPassword, amount);
    }

    public boolean wasRequestedFor(CardId expectedCardId) {
        return cardId.equals(expectedCardId);
    }

    @Override
    public String toString() {
        return "Transaction[cardId=%s, providedPassword=[REDACTED], amount=%s]"
                .formatted(cardId, amount.value());
    }
}
