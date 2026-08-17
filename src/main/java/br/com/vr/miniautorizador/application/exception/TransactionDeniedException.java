package br.com.vr.miniautorizador.application.exception;

import java.util.Objects;

import br.com.vr.miniautorizador.application.port.in.TransactionAuthorizationResult;

public final class TransactionDeniedException extends RuntimeException {

    private final TransactionAuthorizationResult result;

    public TransactionDeniedException(TransactionAuthorizationResult result) {
        super("Transaction was denied: " + Objects.requireNonNull(result, "Result is required"));
        this.result = result;
    }

    public TransactionAuthorizationResult result() {
        return result;
    }
}
