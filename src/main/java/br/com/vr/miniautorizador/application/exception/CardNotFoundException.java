package br.com.vr.miniautorizador.application.exception;

public final class CardNotFoundException extends RuntimeException {

    public CardNotFoundException() {
        super("Card was not found");
    }
}
