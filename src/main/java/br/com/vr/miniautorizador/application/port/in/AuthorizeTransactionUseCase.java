package br.com.vr.miniautorizador.application.port.in;

import br.com.vr.miniautorizador.domain.model.Transaction;

public interface AuthorizeTransactionUseCase {

    TransactionAuthorizationResult authorize(Transaction transaction);
}
