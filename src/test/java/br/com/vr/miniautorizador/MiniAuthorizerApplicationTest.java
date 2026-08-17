package br.com.vr.miniautorizador;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MiniAuthorizerApplicationTest {

    @Test
    void exposesApplicationEntryPoint() {
        assertThat(MiniAuthorizerApplication.class).isPublic();
    }
}
