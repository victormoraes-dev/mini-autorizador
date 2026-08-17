package br.com.vr.miniautorizador.api.dto;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "RFC 9457 Problem Details response")
public record ProblemResponse(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String code,
        List<Violation> violations) {

    public record Violation(String field, String message) {
    }
}
