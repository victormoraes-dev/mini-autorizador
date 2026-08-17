package br.com.vr.miniautorizador.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.vr.miniautorizador.application.port.in.CardDetails;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Safe card representation")
public record CardResponse(
        @Schema(description = "Opaque public card identifier")
        UUID id,

        @Schema(description = "Masked card number", example = "************4501")
        String cardNumber,

        @Schema(description = "Current available balance", example = "500.00")
        BigDecimal balance) {

    public static CardResponse from(CardDetails details) {
        return new CardResponse(details.id(), details.maskedCardNumber(), details.balance());
    }
}
