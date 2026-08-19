package br.com.vr.miniautorizador.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class TransactionAuthorizerTest {

    private static final CardId ID = CardId.from("7c97bca5-3c85-4a2d-aab8-2d06112b56e4");
    private static final CardNumber NUMBER = new CardNumber("6549873025634501");
    private static final PasswordHash HASH = new PasswordHash("test::1234");

    @Mock
    private CardRepository repository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private TransactionAuthorizer authorizer;

    @Test
    void authorizesAndDebitsValidTransactionByOpaqueId() {
        Card card = restoredCard();
        Money amount = Money.of("10.00");
        when(repository.findById(ID)).thenReturn(Optional.of(card));
        when(passwordHasher.matches(new CardPassword("1234"), HASH)).thenReturn(true);
        when(repository.debitIfBalanceIsAvailable(ID, amount)).thenReturn(true);

        TransactionAuthorizationResult result = authorizer.authorize(transaction("1234", amount));

        assertThat(result).isEqualTo(TransactionAuthorizationResult.APPROVED);
        verify(repository).debitIfBalanceIsAvailable(ID, amount);
        assertThat(card.balance().value()).isEqualByComparingTo("490.00");
    }

    @Test
    void prioritizesMissingCardBeforePasswordAndBalance() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThat(authorizer.authorize(transaction("9999", Money.of("600.00"))))
                .isEqualTo(TransactionAuthorizationResult.CARD_NOT_FOUND);
        verifyNoInteractions(passwordHasher);
        verify(repository, never()).debitIfBalanceIsAvailable(ID, Money.of("600.00"));
    }

    @Test
    void prioritizesInvalidPasswordBeforeInsufficientBalance() {
        Card card = restoredCard();
        when(repository.findById(ID)).thenReturn(Optional.of(card));
        when(passwordHasher.matches(new CardPassword("9999"), HASH)).thenReturn(false);

        assertThat(authorizer.authorize(transaction("9999", Money.of("600.00"))))
                .isEqualTo(TransactionAuthorizationResult.INVALID_PASSWORD);
        verify(repository, never()).debitIfBalanceIsAvailable(ID, Money.of("600.00"));
        assertThat(card.balance()).isEqualTo(Balance.INITIAL);
    }

    @Test
    void rejectsInsufficientBalanceWithoutPersistenceDebit() {
        Money amount = Money.of("500.01");
        when(repository.findById(ID)).thenReturn(Optional.of(restoredCard()));
        when(passwordHasher.matches(new CardPassword("1234"), HASH)).thenReturn(true);

        assertThat(authorizer.authorize(transaction("1234", amount)))
                .isEqualTo(TransactionAuthorizationResult.INSUFFICIENT_BALANCE);
        verify(repository, never()).debitIfBalanceIsAvailable(ID, amount);
    }

    @Test
    void convertsLostConcurrencyRaceIntoInsufficientBalance() {
        Money amount = Money.of("10.00");
        when(repository.findById(ID)).thenReturn(Optional.of(restoredCard()));
        when(passwordHasher.matches(new CardPassword("1234"), HASH)).thenReturn(true);
        when(repository.debitIfBalanceIsAvailable(ID, amount)).thenReturn(false);

        assertThat(authorizer.authorize(transaction("1234", amount)))
                .isEqualTo(TransactionAuthorizationResult.INSUFFICIENT_BALANCE);
        verify(repository).debitIfBalanceIsAvailable(ID, amount);
    }

    private static Card restoredCard() {
        return Card.restore(ID, NUMBER, HASH, Balance.INITIAL);
    }

    private static Transaction transaction(String password, Money amount) {
        return Transaction.request(ID, new CardPassword(password), amount);
    }
}
