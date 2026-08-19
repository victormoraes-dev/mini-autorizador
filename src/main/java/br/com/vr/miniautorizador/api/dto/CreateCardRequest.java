package br.com.vr.miniautorizador.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCardRequest(
        
        @Schema(description = "Card number", example = "6549873025634501")
        @NotBlank
        @Size(max = 32)
        String cardNumber,

        @Schema(description = "Card password", example = "1234", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(max = 72)
        String password) {
}
