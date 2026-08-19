package br.com.vr.miniautorizador.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.vr.miniautorizador.application.exception.CardAlreadyExistsException;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.Balance;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.PasswordHash;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

@ExtendWith(MockitoExtension.class)
class CardApplicationServiceTest {

    private static final CardId ID = CardId.from("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final CardPassword PASSWORD = new CardPassword("1234");

    private static final PasswordHash HASH = new PasswordHash("encoded-password");

    @Mock
    private CardRepository repository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private CardApplicationService service;

    @Test
    void createsCardAndReturnsOnlySafeDetails() {
        when(passwordHasher.hash(PASSWORD)).thenReturn(HASH);
        when(repository.create(any(Card.class))).thenReturn(true);

        CardDetails result = service.create(NUMBER, PASSWORD);

        assertThat(result.id()).isNotNull();
        assertThat(result.maskedCardNumber()).isEqualTo("************4501");
        assertThat(result.balance()).isEqualByComparingTo("500.00");
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(repository).create(cardCaptor.capture());
        assertThat(cardCaptor.getValue().passwordHash()).isEqualTo(HASH);
    }

    @Test
    void rejectsKnownDuplicateWithoutHashingPasswordAgain() {
        when(repository.existsByNumber(NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.create(NUMBER, PASSWORD))
                .isInstanceOf(CardAlreadyExistsException.class);
        verify(passwordHasher, never()).hash(any());
        verify(repository, never()).create(any());
    }

    @Test
    void convertsConcurrentInsertIntoConflict() {
        when(passwordHasher.hash(PASSWORD)).thenReturn(HASH);
        when(repository.create(any(Card.class))).thenReturn(false);

        assertThatThrownBy(() -> service.create(NUMBER, PASSWORD))
                .isInstanceOf(CardAlreadyExistsException.class);
        verify(repository).create(any(Card.class));
    }

    @Test
    void returnsExistingCardByOpaqueId() {
        when(repository.findById(ID)).thenReturn(Optional.of(restoredCard()));

        CardDetails details = service.get(ID);

        assertThat(details.id()).isEqualTo(ID.value());
        assertThat(details.maskedCardNumber()).isEqualTo("************4501");
        assertThat(details.balance()).isEqualByComparingTo("500.00");
    }

    @Test
    void reportsMissingCardWithoutLeakingSensitiveData() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessageNotContaining(NUMBER.value());
    }

    private static Card restoredCard() {
        return Card.restore(ID, NUMBER, HASH, Balance.INITIAL);
    }
}
