package br.com.vr.miniautorizador.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
final class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "X-API-Key";

    private final ApiSecurityProperties properties;

    ApiKeyAuthenticationFilter(ApiSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String suppliedKey = request.getHeader(HEADER_NAME);
        if (matches(suppliedKey, properties.writerKey())) {
            authenticate("writer-client", List.of(
                    new SimpleGrantedAuthority("ROLE_READER"),
                    new SimpleGrantedAuthority("ROLE_WRITER")));
        } else if (matches(suppliedKey, properties.readerKey())) {
            authenticate("reader-client", List.of(new SimpleGrantedAuthority("ROLE_READER")));
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticate(String principal, List<SimpleGrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }

    private static boolean matches(String supplied, String expected) {
        return supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
