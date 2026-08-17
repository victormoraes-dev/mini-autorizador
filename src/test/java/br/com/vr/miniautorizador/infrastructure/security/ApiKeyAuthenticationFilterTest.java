package br.com.vr.miniautorizador.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthenticationFilterTest {

    private static final String READER_KEY = "reader-key-with-at-least-32-characters";
    private static final String WRITER_KEY = "writer-key-with-at-least-32-characters";

    private final ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
            new ApiSecurityProperties(READER_KEY, WRITER_KEY, 120, 60));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsReaderAndWriterRolesToWriterKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, WRITER_KEY);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_READER", "ROLE_WRITER");
    }

    @Test
    void leavesInvalidKeyUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, "invalid-key");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
