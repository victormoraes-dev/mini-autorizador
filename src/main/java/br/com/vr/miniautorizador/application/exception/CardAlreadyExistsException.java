package br.com.vr.miniautorizador.application.exception;

public final class CardAlreadyExistsException extends RuntimeException {

    public CardAlreadyExistsException() {
        super("Card already exists");
    }
}
