package br.com.vr.miniautorizador.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.vr.miniautorizador.domain.service.PasswordHasher;

class CardTest {

    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final CardPassword PASSWORD = new CardPassword("1234");
    private static final PasswordHasher HASHER = new ReversibleTestPasswordHasher();

    @Test
    void issuesCardWithInitialBalanceAndProtectedPassword() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);

        assertThat(card.id()).isNotNull();
        assertThat(card.number()).isEqualTo(NUMBER);
        assertThat(card.balance()).isEqualTo(Balance.INITIAL);
        assertThat(card.passwordHash().value()).isNotEqualTo(PASSWORD.value());
    }

    @Test
    void approvesTransactionWhenPasswordAndBalanceAreValid() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);
        Transaction transaction = transaction(card.id(), "1234", "10.00");

        assertThat(card.authorize(transaction, HASHER)).isEqualTo(AuthorizationResult.APPROVED);
    }

    @Test
    void rejectsInvalidPasswordBeforeEvaluatingBalance() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);
        Transaction transaction = transaction(card.id(), "9999", "600.00");

        assertThat(card.authorize(transaction, HASHER)).isEqualTo(AuthorizationResult.INVALID_PASSWORD);
    }

    @Test
    void rejectsTransactionWhenBalanceIsInsufficient() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);
        Transaction transaction = transaction(card.id(), "1234", "500.01");

        assertThat(card.authorize(transaction, HASHER)).isEqualTo(AuthorizationResult.INSUFFICIENT_BALANCE);
    }

    @Test
    void debitsAnAuthorizedAmount() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);

        card.debit(Money.of("10.00"));

        assertThat(card.balance().value()).isEqualByComparingTo("490.00");
    }

    @Test
    void refusesDebitThatWouldMakeBalanceNegative() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);

        assertThatThrownBy(() -> card.debit(Money.of("500.01")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(card.balance()).isEqualTo(Balance.INITIAL);
    }

    @Test
    void redactsSensitiveDataFromStringRepresentation() {
        Card card = Card.issue(NUMBER, PASSWORD, HASHER);
        Transaction transaction = transaction(card.id(), "1234", "10.00");

        assertThat(card.toString())
                .doesNotContain(NUMBER.value(), PASSWORD.value())
                .contains("************4501", "[REDACTED]");
        assertThat(transaction.toString())
                .doesNotContain(NUMBER.value(), PASSWORD.value())
                .contains(card.id().toString(), "[REDACTED]");
    }

    private static Transaction transaction(CardId cardId, String password, String amount) {
        return Transaction.request(cardId, new CardPassword(password), Money.of(amount));
    }

    private static final class ReversibleTestPasswordHasher implements PasswordHasher {

        @Override
        public PasswordHash hash(CardPassword password) {
            return new PasswordHash("test::" + password.value());
        }

        @Override
        public boolean matches(CardPassword password, PasswordHash hash) {
            return hash.equals(hash(password));
        }
    }
}
