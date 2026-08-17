package br.com.vr.miniautorizador.application.port.out;

import java.util.Optional;

import br.com.vr.miniautorizador.domain.model.Card;
import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.Money;

public interface CardRepository {

    boolean existsByNumber(CardNumber cardNumber);

    boolean create(Card card);

    Optional<Card> findById(CardId cardId);

    boolean debitIfBalanceIsAvailable(CardId cardId, Money amount);
}
