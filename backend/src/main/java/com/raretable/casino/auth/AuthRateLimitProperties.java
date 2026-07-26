package com.raretable.casino.auth;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("raretable.auth.rate-limit")
public record AuthRateLimitProperties(
    Duration window,
    Limits challenge,
    Limits verification
)
{
    public AuthRateLimitProperties
    {
        if (window == null || window.toMillis() <= 0)
        {
            throw new IllegalArgumentException("Authentication rate-limit window must be positive");
        }

        Objects.requireNonNull(challenge, "Challenge rate limits are required");
        Objects.requireNonNull(verification, "Verification rate limits are required");
    }

    public record Limits(
        int requestsPerSource,
        int requestsPerWallet,
        int requestsGlobal
    )
    {
        public Limits
        {
            if (requestsPerSource <= 0
                || requestsPerWallet <= 0
                || requestsGlobal <= 0)
            {
                throw new IllegalArgumentException(
                    "Authentication rate limits must be positive"
                );
            }
        }
    }
}
