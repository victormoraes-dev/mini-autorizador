package br.com.vr.miniautorizador.infrastructure.security;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@ConditionalOnProperty(name = "app.security.require-tls", havingValue = "true")
public final class TlsEnforcementFilter extends OncePerRequestFilter {

    private final SecurityProblemWriter problemWriter;

    TlsEnforcementFilter(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!request.isSecure()) {
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.UPGRADE_REQUIRED.value(),
                    "tls-required",
                    "TLS required",
                    "This endpoint is available only over a secure connection",
                    "TLS_REQUIRED");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
