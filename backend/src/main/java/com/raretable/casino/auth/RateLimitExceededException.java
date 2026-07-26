package com.raretable.casino.auth;

import java.time.Duration;

public final class RateLimitExceededException extends RuntimeException
{
    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter)
    {
        super("Too many authentication requests");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds()
    {
        long milliseconds = retryAfter.toMillis();
        return Math.max(1, (milliseconds + 999) / 1000);
    }
}
