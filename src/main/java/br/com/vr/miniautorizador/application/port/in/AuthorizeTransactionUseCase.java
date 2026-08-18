package br.com.vr.miniautorizador.application.port.in;

import br.com.vr.miniautorizador.domain.model.Transaction;
import br.com.vr.miniautorizador.domain.model.CardNumber;
import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.Money;

public interface AuthorizeTransactionUseCase {

    TransactionAuthorizationResult authorize(Transaction transaction);

    default TransactionAuthorizationResult authorize(CardNumber cardNumber, CardPassword password, Money amount) {
        throw new UnsupportedOperationException("Card number authorization is not supported");
    }
}
