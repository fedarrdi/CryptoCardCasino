package com.raretable.casino.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("raretable.auth.session")
public record WalletSessionProperties(
    Duration absoluteTimeout
)
{
    public WalletSessionProperties
    {
        if (absoluteTimeout == null || absoluteTimeout.isZero() || absoluteTimeout.isNegative())
        {
            throw new IllegalArgumentException("Absolute session timeout must be positive");
        }
    }
}
