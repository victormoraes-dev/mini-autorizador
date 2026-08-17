package br.com.vr.miniautorizador.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.vr.miniautorizador.application.exception.CardAlreadyExistsException;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.Balance;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;
import br.com.vr.miniautorizador.domain.model.PasswordHash;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

class CardApplicationServiceTest {

    private static final CardId ID = CardId.from("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final CardPassword PASSWORD = new CardPassword("1234");

    private RecordingCardRepository repository;
    private RecordingPasswordHasher passwordHasher;
    private CardApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingCardRepository();
        passwordHasher = new RecordingPasswordHasher();
        service = new CardApplicationService(repository, passwordHasher);
    }

    @Test
    void createsCardAndReturnsOnlySafeDetails() {
        CardDetails result = service.create(NUMBER, PASSWORD);

        assertThat(result.id()).isNotNull();
        assertThat(result.maskedCardNumber()).isEqualTo("************4501");
        assertThat(result.balance()).isEqualByComparingTo("500.00");
        assertThat(repository.card.passwordHash()).isEqualTo(new PasswordHash("encoded-password"));
    }

    @Test
    void rejectsKnownDuplicateWithoutHashingPasswordAgain() {
        repository.card = restoredCard();

        assertThatThrownBy(() -> service.create(NUMBER, PASSWORD))
                .isInstanceOf(CardAlreadyExistsException.class);
        assertThat(passwordHasher.hashCalls).isZero();
        assertThat(repository.createCalls).isZero();
    }

    @Test
    void convertsConcurrentInsertIntoConflict() {
        repository.createSucceeds = false;

        assertThatThrownBy(() -> service.create(NUMBER, PASSWORD))
                .isInstanceOf(CardAlreadyExistsException.class);
    }

    @Test
    void returnsExistingCardByOpaqueId() {
        repository.card = restoredCard();

        CardDetails details = service.get(ID);

        assertThat(details.id()).isEqualTo(ID.value());
        assertThat(details.maskedCardNumber()).isEqualTo("************4501");
        assertThat(details.balance()).isEqualByComparingTo("500.00");
    }

    @Test
    void reportsMissingCardWithoutLeakingSensitiveData() {
        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessageNotContaining(NUMBER.value());
    }

    private static Card restoredCard() {
        return Card.restore(ID, NUMBER, new PasswordHash("encoded-password"), Balance.INITIAL);
    }

    private static final class RecordingPasswordHasher implements PasswordHasher {

        private int hashCalls;

        @Override
        public PasswordHash hash(CardPassword password) {
            hashCalls++;
            return new PasswordHash("encoded-password");
        }

        @Override
        public boolean matches(CardPassword password, PasswordHash hash) {
            return false;
        }
    }

    private static final class RecordingCardRepository implements CardRepository {

        private Card card;
        private int createCalls;
        private boolean createSucceeds = true;

        @Override
        public boolean existsByNumber(CardNumber cardNumber) {
            return card != null;
        }

        @Override
        public boolean create(Card newCard) {
            createCalls++;
            if (createSucceeds) {
                card = newCard;
            }
            return createSucceeds;
        }

        @Override
        public Optional<Card> findById(CardId cardId) {
            return card != null && card.id().equals(cardId) ? Optional.of(card) : Optional.empty();
        }

        @Override
        public boolean debitIfBalanceIsAvailable(CardId cardId, Money amount) {
            throw new UnsupportedOperationException("Not needed by card service tests");
        }
    }
}
