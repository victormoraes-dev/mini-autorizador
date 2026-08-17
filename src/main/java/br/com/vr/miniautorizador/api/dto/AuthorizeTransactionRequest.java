package br.com.vr.miniautorizador.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuthorizeTransactionRequest(
        @Schema(description = "Opaque public card identifier", example = "7c97bca5-3c85-4a2d-aab8-2d06112b56e4")
        @NotBlank
        @Size(max = 36)
        String cardId,

        @Schema(description = "Password provided for authorization", example = "1234", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(max = 72)
        String password,

        @Schema(description = "Positive transaction amount with up to two decimal places", example = "10.00")
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {
}
