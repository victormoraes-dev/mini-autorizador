package br.com.vr.miniautorizador.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.vr.miniautorizador.domain.model.Card;

public record CardDetails(UUID id, String maskedCardNumber, BigDecimal balance) {

    public static CardDetails from(Card card) {
        return new CardDetails(
                card.id().value(),
                card.number().masked(),
                card.balance().value());
    }
}
