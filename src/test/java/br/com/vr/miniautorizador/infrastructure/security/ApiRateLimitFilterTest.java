package br.com.vr.miniautorizador.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

class ApiRateLimitFilterTest {

    @Test
    void returnsStructured429AfterClientExhaustsWindow() throws Exception {
        ApiSecurityProperties properties = new ApiSecurityProperties(
                "reader-key-with-at-least-32-characters",
                "writer-key-with-at-least-32-characters",
                2,
                60);
        SecurityProblemWriter problemWriter = new SecurityProblemWriter(new ObjectMapper());
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                properties,
                problemWriter,
                Clock.fixed(Instant.parse("2026-08-17T13:00:00Z"), ZoneOffset.UTC));

        assertThat(invoke(filter).getStatus()).isEqualTo(200);
        assertThat(invoke(filter).getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = invoke(filter);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    private static MockHttpServletResponse invoke(ApiRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cards");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
