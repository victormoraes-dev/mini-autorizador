package br.com.vr.miniautorizador.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.vr.miniautorizador.domain.model.CardPassword;
import br.com.vr.miniautorizador.domain.model.PasswordHash;
import br.com.vr.miniautorizador.domain.service.PasswordHasher;

@Component
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT_ALGORITHM = "pbkdf2-sha256";
    private static final String SEPARATOR = "$";

    private final PasswordSecurityProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public Pbkdf2PasswordHasher(PasswordSecurityProperties properties) {
        this(properties, new SecureRandom());
    }

    Pbkdf2PasswordHasher(PasswordSecurityProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    @Override
    public PasswordHash hash(CardPassword password) {
        byte[] salt = new byte[properties.saltLength()];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = derive(password, salt, properties.iterations(), properties.keyLength());

        try {
            String encoded = String.join(
                    SEPARATOR,
                    FORMAT_ALGORITHM,
                    Integer.toString(properties.iterations()),
                    Base64.getEncoder().encodeToString(salt),
                    Base64.getEncoder().encodeToString(derivedKey));
            return new PasswordHash(encoded);
        } finally {
            Arrays.fill(derivedKey, (byte) 0);
        }
    }

    @Override
    public boolean matches(CardPassword password, PasswordHash hash) {

        try {
            String[] parts = hash.value().split("\\$", -1);

            if (parts.length != 4 || !FORMAT_ALGORITHM.equals(parts[0])) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);

            if (iterations < 100_000 || iterations > 1_000_000
                    || salt.length < 16 || salt.length > 64
                    || expected.length < 32 || expected.length > 64) {
                return false;
            }

            byte[] actual = derive(password, salt, iterations, expected.length * Byte.SIZE);

            try {
                return MessageDigest.isEqual(expected, actual);
            } finally {
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(actual, (byte) 0);
            }
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(CardPassword password, byte[] salt, int iterations, int keyLength) {
        char[] secret = combine(password.value(), properties.pepper());
        PBEKeySpec keySpec = new PBEKeySpec(secret, salt, iterations, keyLength);

        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("PBKDF2 password hashing is unavailable", exception);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(secret, '\0');
        }
    }

    private static char[] combine(String password, String pepper) {
        char[] result = new char[password.length() + pepper.length()];
        password.getChars(0, password.length(), result, 0);
        pepper.getChars(0, pepper.length(), result, password.length());
        return result;
    }
}
