package br.com.vr.miniautorizador.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
final class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final ApiSecurityProperties properties;
    private final SecurityProblemWriter problemWriter;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    ApiRateLimitFilter(ApiSecurityProperties properties, SecurityProblemWriter problemWriter) {
        this(properties, problemWriter, Clock.systemUTC());
    }

    ApiRateLimitFilter(
            ApiSecurityProperties properties,
            SecurityProblemWriter problemWriter,
            Clock clock) {
        this.properties = properties;
        this.problemWriter = problemWriter;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long now = clock.millis();
        String client = clientFingerprint(request);
        Window window = windows.computeIfAbsent(client, ignored -> new Window(now));
        if (!window.allow(now, properties.windowSeconds() * 1_000L, properties.requestsPerWindow())) {
            response.setHeader("Retry-After", Integer.toString(properties.windowSeconds()));
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "rate-limit-exceeded",
                    "Too many requests",
                    "The request rate limit has been exceeded",
                    "RATE_LIMIT_EXCEEDED");
            return;
        }
        discardExpiredWindows(now);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private void discardExpiredWindows(long now) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        long oldestAllowed = now - properties.windowSeconds() * 1_000L;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt() < oldestAllowed);
    }

    private static String clientFingerprint(HttpServletRequest request) {
        String suppliedKey = request.getHeader(ApiKeyAuthenticationFilter.HEADER_NAME);
        String identity = suppliedKey == null || suppliedKey.isBlank()
                ? "ip:" + request.getRemoteAddr()
                : "key:" + suppliedKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class Window {

        private long startedAt;
        private int requests;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized boolean allow(long now, long duration, int limit) {
            if (now - startedAt >= duration) {
                startedAt = now;
                requests = 0;
            }
            requests++;
            return requests <= limit;
        }

        private synchronized long startedAt() {
            return startedAt;
        }
    }
}
