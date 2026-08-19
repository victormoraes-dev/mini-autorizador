package br.com.vr.miniautorizador.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.vr.miniautorizador.domain.service.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class CardTest {

    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final CardPassword PASSWORD = new CardPassword("1234");
    private static final PasswordHash HASH = new PasswordHash("encoded-password");

    @Mock
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        when(passwordHasher.hash(PASSWORD)).thenReturn(HASH);
    }

    @Test
    void issuesCardWithInitialBalanceAndProtectedPassword() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);

        assertThat(card.id()).isNotNull();
        assertThat(card.number()).isEqualTo(NUMBER);
        assertThat(card.balance()).isEqualTo(Balance.INITIAL);
        assertThat(card.passwordHash()).isEqualTo(HASH);
        verify(passwordHasher).hash(PASSWORD);
    }

    @Test
    void approvesTransactionWhenPasswordAndBalanceAreValid() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);
        Transaction transaction = transaction(card.id(), "1234", "10.00");
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);

        assertThat(card.authorize(transaction, passwordHasher)).isEqualTo(AuthorizationResult.APPROVED);
    }

    @Test
    void rejectsInvalidPasswordBeforeEvaluatingBalance() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);
        Transaction transaction = transaction(card.id(), "9999", "600.00");
        when(passwordHasher.matches(new CardPassword("9999"), HASH)).thenReturn(false);

        assertThat(card.authorize(transaction, passwordHasher)).isEqualTo(AuthorizationResult.INVALID_PASSWORD);
    }

    @Test
    void rejectsTransactionWhenBalanceIsInsufficient() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);
        Transaction transaction = transaction(card.id(), "1234", "500.01");
        when(passwordHasher.matches(PASSWORD, HASH)).thenReturn(true);

        assertThat(card.authorize(transaction, passwordHasher))
                .isEqualTo(AuthorizationResult.INSUFFICIENT_BALANCE);
    }

    @Test
    void debitsAnAuthorizedAmount() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);

        card.debit(Money.of("10.00"));

        assertThat(card.balance().value()).isEqualByComparingTo("490.00");
    }

    @Test
    void refusesDebitThatWouldMakeBalanceNegative() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);

        assertThatThrownBy(() -> card.debit(Money.of("500.01")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(card.balance()).isEqualTo(Balance.INITIAL);
    }

    @Test
    void redactsSensitiveDataFromStringRepresentation() {
        Card card = Card.issue(NUMBER, PASSWORD, passwordHasher);
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
}
