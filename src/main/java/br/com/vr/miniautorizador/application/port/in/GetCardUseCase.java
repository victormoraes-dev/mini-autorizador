package br.com.vr.miniautorizador.application.port.in;

import br.com.vr.miniautorizador.domain.model.CardId;

public interface GetCardUseCase {

    CardDetails get(CardId cardId);
}
