package br.com.vr.miniautorizador.api.dto;

import java.math.BigDecimal;

public record AuthorizeTransactionRequest(
        String numeroCartao,
        String senhaCartao,
        BigDecimal valor) {
}
