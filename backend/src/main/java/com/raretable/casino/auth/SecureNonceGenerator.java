package com.raretable.casino.auth;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public final class SecureNonceGenerator
{
    private static final int NONCE_BYTES = 16;

    private final SecureRandom secureRandom;

    public SecureNonceGenerator()
    {
        this.secureRandom = new SecureRandom();
    }

    public String generate()
    {
        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
