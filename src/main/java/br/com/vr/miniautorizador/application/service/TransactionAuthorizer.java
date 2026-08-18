package br.com.vr.miniautorizador.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.vr.miniautorizador.application.port.in.AuthorizeTransactionUseCase;
import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;
import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.AuthorizationResult;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;
import br.com.vr.miniautorizador.domain.model.Transaction;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

@Service
public class TransactionAuthorizer implements AuthorizeTransactionUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionAuthorizer.class);

    private final CardRepository cardRepository;
    private final PasswordHasher passwordHasher;

    public TransactionAuthorizer(CardRepository cardRepository, PasswordHasher passwordHasher) {
        this.cardRepository = cardRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public TransactionAuthorizationResult authorize(Transaction transaction) {
        Card card = cardRepository.findById(transaction.cardId()).orElse(null);
        if (card == null) {
            LOGGER.info("Transaction denied because card {} was not found", transaction.cardId());
            return TransactionAuthorizationResult.CARD_NOT_FOUND;
        }

        return authorize(card, transaction);
    }

    @Override
    @Transactional
    public TransactionAuthorizationResult authorize(CardNumber cardNumber, CardPassword password, Money amount) {
        Card card = cardRepository.findByNumber(cardNumber).orElse(null);
        if (card == null) {
            LOGGER.info("Transaction denied because card {} was not found", cardNumber);
            return TransactionAuthorizationResult.CARD_NOT_FOUND;
        }
        return authorize(card, Transaction.request(card.id(), password, amount));
    }

    private TransactionAuthorizationResult authorize(Card card, Transaction transaction) {
        AuthorizationResult domainResult = card.authorize(transaction, passwordHasher);
        if (domainResult == AuthorizationResult.INVALID_PASSWORD) {
            LOGGER.info("Transaction denied by password for card {}", transaction.cardId());
            return TransactionAuthorizationResult.INVALID_PASSWORD;
        }
        if (domainResult == AuthorizationResult.INSUFFICIENT_BALANCE) {
            LOGGER.info("Transaction denied by balance for card {}", transaction.cardId());
            return TransactionAuthorizationResult.INSUFFICIENT_BALANCE;
        }

        card.debit(transaction.amount());
        boolean debited = cardRepository.debitIfBalanceIsAvailable(
                transaction.cardId(),
                transaction.amount());
        if (!debited) {
            LOGGER.info("Transaction denied after concurrent debit for card {}", transaction.cardId());
            return TransactionAuthorizationResult.INSUFFICIENT_BALANCE;
        }

        LOGGER.info("Transaction authorized for card {}", transaction.cardId());
        return TransactionAuthorizationResult.APPROVED;
    }
}
