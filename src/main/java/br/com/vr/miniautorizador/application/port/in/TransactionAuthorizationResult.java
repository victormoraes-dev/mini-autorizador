package br.com.vr.miniautorizador.application.port.in;

public enum TransactionAuthorizationResult {
    APPROVED,
    CARD_NOT_FOUND,
    INVALID_PASSWORD,
    INSUFFICIENT_BALANCE
}
