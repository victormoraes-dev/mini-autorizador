package br.com.vr.miniautorizador.application.port.in;

import br.com.vr.miniautorizador.domain.model.CardId;
import br.com.vr.miniautorizador.domain.model.CardNumber;

public interface GetCardUseCase {

    CardDetails get(CardId cardId);

    default CardDetails get(CardNumber cardNumber) {
        throw new UnsupportedOperationException("Card number lookup is not supported");
    }
}
