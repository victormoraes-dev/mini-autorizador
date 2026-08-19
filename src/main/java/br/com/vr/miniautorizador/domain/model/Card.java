package br.com.vr.miniautorizador.domain.model;

import java.util.Objects;

import br.com.vr.miniautorizador.domain.service.PasswordHasher;

public final class Card {

    private final CardId id;
    private final CardNumber number;
    private final PasswordHash passwordHash;
    private Balance balance;

    private Card(CardId id, CardNumber number, PasswordHash passwordHash, Balance balance) {
        this.id = Objects.requireNonNull(id, "Card id is required");
        this.number = Objects.requireNonNull(number, "Card number is required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash is required");
        this.balance = Objects.requireNonNull(balance, "Balance is required");
    }

    public static Card issue(CardNumber number, CardPassword password, PasswordHasher passwordHasher) {
        Objects.requireNonNull(password, "Card password is required");
        Objects.requireNonNull(passwordHasher, "Password hasher is required");
        
        return new Card(CardId.generate(), number, passwordHasher.hash(password), Balance.INITIAL);
    }

    public static Card restore(CardId id, CardNumber number, PasswordHash passwordHash, Balance balance) {
        return new Card(id, number, passwordHash, balance);
    }

    public AuthorizationResult authorize(Transaction transaction, PasswordHasher passwordHasher) {
        Objects.requireNonNull(transaction, "Transaction is required");
        Objects.requireNonNull(passwordHasher, "Password hasher is required");

        if (!transaction.wasRequestedFor(id)
                || !passwordHasher.matches(transaction.providedPassword(), passwordHash)) {
            return AuthorizationResult.INVALID_PASSWORD;
        }
        
        if (!balance.canCover(transaction.amount())) {
            return AuthorizationResult.INSUFFICIENT_BALANCE;
        }
        return AuthorizationResult.APPROVED;
    }

    public void debit(Money amount) {
        balance = balance.debit(amount);
    }

    public CardId id() {
        return id;
    }

    public CardNumber number() {
        return number;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Balance balance() {
        return balance;
    }

    @Override
    public String toString() {
        return "Card[id=%s, number=%s, passwordHash=[REDACTED], balance=%s]"
                .formatted(id, number.masked(), balance.value());
    }
}
