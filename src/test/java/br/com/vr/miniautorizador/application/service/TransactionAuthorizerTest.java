package br.com.vr.miniautorizador.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;
import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.Balance;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;
import br.com.vr.miniautorizador.domain.model.PasswordHash;
import br.com.vr.miniautorizador.domain.model.Transaction;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

class TransactionAuthorizerTest {

    private static final CardId ID = CardId.from("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final PasswordHash HASH = new PasswordHash("test::1234");

    private RecordingCardRepository repository;
    private TransactionAuthorizer authorizer;

    @BeforeEach
    void setUp() {
        repository = new RecordingCardRepository();
        authorizer = new TransactionAuthorizer(repository, new TestPasswordHasher());
    }

    @Test
    void authorizesAndDebitsValidTransactionByOpaqueId() {
        repository.card = restoredCard();

        TransactionAuthorizationResult result = authorizer.authorize(transaction("1234", "10.00"));

        assertThat(result).isEqualTo(TransactionAuthorizationResult.APPROVED);
        assertThat(repository.debitAttempts).isEqualTo(1);
        assertThat(repository.lastDebitedId).isEqualTo(ID);
        assertThat(repository.card.balance().value()).isEqualByComparingTo("490.00");
    }

    @Test
    void prioritizesMissingCardBeforePasswordAndBalance() {
        assertThat(authorizer.authorize(transaction("9999", "600.00")))
                .isEqualTo(TransactionAuthorizationResult.CARD_NOT_FOUND);
        assertThat(repository.debitAttempts).isZero();
    }

    @Test
    void prioritizesInvalidPasswordBeforeInsufficientBalance() {
        repository.card = restoredCard();

        assertThat(authorizer.authorize(transaction("9999", "600.00")))
                .isEqualTo(TransactionAuthorizationResult.INVALID_PASSWORD);
        assertThat(repository.debitAttempts).isZero();
        assertThat(repository.card.balance()).isEqualTo(Balance.INITIAL);
    }

    @Test
    void rejectsInsufficientBalanceWithoutPersistenceDebit() {
        repository.card = restoredCard();

        assertThat(authorizer.authorize(transaction("1234", "500.01")))
                .isEqualTo(TransactionAuthorizationResult.INSUFFICIENT_BALANCE);
        assertThat(repository.debitAttempts).isZero();
    }

    @Test
    void convertsLostConcurrencyRaceIntoInsufficientBalance() {
        repository.card = restoredCard();
        repository.atomicDebitSucceeds = false;

        assertThat(authorizer.authorize(transaction("1234", "10.00")))
                .isEqualTo(TransactionAuthorizationResult.INSUFFICIENT_BALANCE);
        assertThat(repository.debitAttempts).isEqualTo(1);
    }

    private static Card restoredCard() {
        return Card.restore(ID, NUMBER, HASH, Balance.INITIAL);
    }

    private static Transaction transaction(String password, String amount) {
        return Transaction.request(ID, new CardPassword(password), Money.of(amount));
    }

    private static final class TestPasswordHasher implements PasswordHasher {

        @Override
        public PasswordHash hash(CardPassword password) {
            return new PasswordHash("test::" + password.value());
        }

        @Override
        public boolean matches(CardPassword password, PasswordHash hash) {
            return hash.equals(hash(password));
        }
    }

    private static final class RecordingCardRepository implements CardRepository {

        private Card card;
        private CardId lastDebitedId;
        private int debitAttempts;
        private boolean atomicDebitSucceeds = true;

        @Override
        public boolean existsByNumber(CardNumber cardNumber) {
            return card != null;
        }

        @Override
        public boolean create(Card newCard) {
            card = newCard;
            return true;
        }

        @Override
        public Optional<Card> findById(CardId cardId) {
            return card != null && card.id().equals(cardId) ? Optional.of(card) : Optional.empty();
        }

        @Override
        public boolean debitIfBalanceIsAvailable(CardId cardId, Money amount) {
            lastDebitedId = cardId;
            debitAttempts++;
            return atomicDebitSucceeds;
        }
    }
}
