package br.com.vr.miniautorizador.domain.model;

public final class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException() {
        super("Card balance is insufficient");
    }
}
