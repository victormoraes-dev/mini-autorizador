package br.com.vr.miniautorizador.infrastructure.security;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
final class SecurityProblemWriter {

    private final ObjectMapper objectMapper;

    SecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String type,
            String title,
            String detail,
            String code) throws IOException {

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        objectMapper.writeValue(response.getOutputStream(), new SecurityProblem(
                URI.create("urn:problem:" + type),
                title,
                status,
                detail,
                URI.create(request.getRequestURI()),
                code));
    }

    private record SecurityProblem(
            URI type,
            String title,
            int status,
            String detail,
            URI instance,
            String code) {
    }
}
