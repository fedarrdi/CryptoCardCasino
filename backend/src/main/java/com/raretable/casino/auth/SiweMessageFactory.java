package com.raretable.casino.auth;

import java.time.Instant;

import org.springframework.stereotype.Component;

@Component
public final class SiweMessageFactory
{
    private static final String STATEMENT = "Sign in to RareTable.";

    private final SiweProperties properties;

    public SiweMessageFactory(SiweProperties properties)
    {
        this.properties = properties;
    }

    public String create(
        String walletAddress,
        long chainId,
        String nonce,
        Instant issuedAt,
        Instant expiresAt
    )
    {
        String origin = properties.uri().getScheme() + "://" + properties.domain();

        return origin + " wants you to sign in with your Ethereum account:\n"
            + walletAddress + "\n\n"
            + STATEMENT + "\n\n"
            + "URI: " + properties.uri() + "\n"
            + "Version: 1\n"
            + "Chain ID: " + chainId + "\n"
            + "Nonce: " + nonce + "\n"
            + "Issued At: " + issuedAt + "\n"
            + "Expiration Time: " + expiresAt;
    }
}
