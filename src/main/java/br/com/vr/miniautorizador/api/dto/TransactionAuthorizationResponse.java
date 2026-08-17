package br.com.vr.miniautorizador.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful transaction authorization result")
public record TransactionAuthorizationResponse(
        @Schema(description = "Authorization status", example = "AUTHORIZED")
        String status) {

    public static TransactionAuthorizationResponse authorized() {
        return new TransactionAuthorizationResponse("AUTHORIZED");
    }
}
