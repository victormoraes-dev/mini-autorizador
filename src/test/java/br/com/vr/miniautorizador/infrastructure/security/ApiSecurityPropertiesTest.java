package br.com.vr.miniautorizador.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiSecurityPropertiesTest {

    private static final String READER_KEY = "reader-key-with-at-least-32-characters";
    private static final String WRITER_KEY = "writer-key-with-at-least-32-characters";

    @Test
    void acceptsDistinctStrongKeysAndPositiveRateLimit() {
        new ApiSecurityProperties(READER_KEY, WRITER_KEY, 120, 60);
    }

    @Test
    void rejectsWeakOrSharedKeys() {
        assertThatThrownBy(() -> new ApiSecurityProperties("short", WRITER_KEY, 120, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiSecurityProperties(READER_KEY, READER_KEY, 120, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidRateLimitConfiguration() {
        assertThatThrownBy(() -> new ApiSecurityProperties(READER_KEY, WRITER_KEY, 0, 60))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApiSecurityProperties(READER_KEY, WRITER_KEY, 120, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
