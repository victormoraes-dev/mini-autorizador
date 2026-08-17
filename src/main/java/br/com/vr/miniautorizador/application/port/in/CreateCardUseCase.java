package br.com.vr.miniautorizador.application.port.in;

import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;

public interface CreateCardUseCase {

    CardDetails create(CardNumber cardNumber, CardPassword password);
}
