package com.raretable.casino.auth;

import java.time.Instant;

public record LoginChallenge(
    String nonce,
    String walletAddress,
    String message,
    Instant expiresAt
)
{
}
