package br.com.vr.miniautorizador.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.vr.miniautorizador.application.exception.CardAlreadyExistsException;
import br.com.vr.miniautorizador.application.exception.CardNotFoundException;
import br.com.vr.miniautorizador.application.port.in.CardDetails;
import br.com.vr.miniautorizador.application.port.in.CreateCardUseCase;
import br.com.vr.miniautorizador.application.port.in.GetCardUseCase;
import br.com.vr.miniautorizador.application.port.out.CardRepository;
import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

@Service
public class CardApplicationService implements CreateCardUseCase, GetCardUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardApplicationService.class);

    private final CardRepository cardRepository;
    private final PasswordHasher passwordHasher;

    public CardApplicationService(CardRepository cardRepository, PasswordHasher passwordHasher) {
        this.cardRepository = cardRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public CardDetails create(CardNumber cardNumber, CardPassword password) {

        if (cardRepository.existsByNumber(cardNumber)) {
            LOGGER.info("Card {} already exists", cardNumber);
            throw new CardAlreadyExistsException();
        }

        Card card = Card.issue(cardNumber, password, passwordHasher);
        if (!cardRepository.create(card)) {
            LOGGER.info("Card {} was concurrently created", cardNumber);
            throw new CardAlreadyExistsException();
        }

        LOGGER.info("Card {} created", cardNumber);
        return CardDetails.from(card);
    }

    @Override
    @Transactional(readOnly = true)
    public CardDetails get(CardId cardId) {
        return cardRepository.findById(cardId)
                .map(CardDetails::from)
                .orElseThrow(CardNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public CardDetails get(CardNumber cardNumber) {
        return cardRepository.findByNumber(cardNumber)
                .map(CardDetails::from)
                .orElseThrow(CardNotFoundException::new);
    }
}
