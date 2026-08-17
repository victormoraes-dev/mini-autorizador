package br.com.vr.miniautorizador.domain.service;

import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.PasswordHash;

public interface PasswordHasher {

    PasswordHash hash(CardPassword password);

    boolean matches(CardPassword password, PasswordHash hash);
}
