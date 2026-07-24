package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SiweMessageFactoryTests
{
    private static final String WALLET_ADDRESS =
        "0x0000000000000000000000000000000000000001";
    private static final String NONCE = "0123456789abcdef";
    private static final Instant ISSUED_AT = Instant.parse("2026-07-24T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-24T10:05:00Z");

    @Test
    void includesHttpDevelopmentOrigin()
    {
        assertEquals(
            "http://localhost:5173 wants you to sign in with your Ethereum account:",
            firstLine(createMessage("localhost:5173", "http://localhost:5173"))
        );
    }

    @Test
    void includesHttpsProductionOrigin()
    {
        assertEquals(
            "https://raretable.example wants you to sign in with your Ethereum account:",
            firstLine(createMessage("raretable.example", "https://raretable.example"))
        );
    }

    private static String createMessage(String domain, String uri)
    {
        SiweProperties properties = new SiweProperties(
            domain,
            URI.create(uri),
            Duration.ofMinutes(5),
            1,
            uri.startsWith("http://")
        );

        return new SiweMessageFactory(properties).create(
            WALLET_ADDRESS,
            1,
            NONCE,
            ISSUED_AT,
            EXPIRES_AT
        );
    }

    private static String firstLine(String message)
    {
        return message.lines().findFirst().orElseThrow();
    }
}
