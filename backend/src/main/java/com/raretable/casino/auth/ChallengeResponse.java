package com.raretable.casino.auth;

import java.time.Instant;

public record ChallengeResponse(
    String nonce,
    String message,
    Instant expiresAt
)
{
}
