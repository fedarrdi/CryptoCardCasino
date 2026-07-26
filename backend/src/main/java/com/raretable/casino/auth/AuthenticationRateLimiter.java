package com.raretable.casino.auth;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public final class AuthenticationRateLimiter
{
    private static final String KEY_PREFIX = "raretable:auth:rate-limit:";

    // Incrementing and expiring every applicable counter in one script prevents split limits.
    private static final RedisScript<Long> RESERVE_SCRIPT = RedisScript.of("""
        local retry_after = 0

        for index, key in ipairs(KEYS) do
            local count = redis.call('INCR', key)

            if count == 1 then
                redis.call('PEXPIRE', key, ARGV[1])
            end

            if count > tonumber(ARGV[index + 1]) then
                local ttl = redis.call('PTTL', key)

                if ttl > retry_after then
                    retry_after = ttl
                end
            end
        end

        return retry_after
        """, Long.class);

    private final StringRedisTemplate redis;
    private final AuthRateLimitProperties properties;

    public AuthenticationRateLimiter(
        StringRedisTemplate redis,
        AuthRateLimitProperties properties
    )
    {
        this.redis = redis;
        this.properties = properties;
    }

    public void reserveChallengeRequest(String source, String walletAddress)
    {
        AuthRateLimitProperties.Limits limits = properties.challenge();

        reserve(List.of(
            new Limit(key("challenge", "source", source), limits.requestsPerSource()),
            new Limit(key("challenge", "wallet", walletAddress), limits.requestsPerWallet()),
            new Limit(key("challenge", "global", "all"), limits.requestsGlobal())
        ));
    }

    public void reserveVerificationRequest(String source)
    {
        AuthRateLimitProperties.Limits limits = properties.verification();

        reserve(List.of(
            new Limit(key("verification", "source", source), limits.requestsPerSource()),
            new Limit(key("verification", "global", "all"), limits.requestsGlobal())
        ));
    }

    public void reserveWalletVerification(String walletAddress)
    {
        AuthRateLimitProperties.Limits limits = properties.verification();

        reserve(List.of(
            new Limit(
                key("verification", "wallet", walletAddress),
                limits.requestsPerWallet()
            )
        ));
    }

    private void reserve(List<Limit> limits)
    {
        List<String> keys = limits.stream()
            .map(Limit::key)
            .toList();
        List<String> arguments = new ArrayList<>(limits.size() + 1);
        arguments.add(Long.toString(properties.window().toMillis()));
        limits.stream()
            .map(Limit::maximumRequests)
            .map(String::valueOf)
            .forEach(arguments::add);

        Long retryAfterMilliseconds = redis.execute(
            RESERVE_SCRIPT,
            keys,
            arguments.toArray()
        );

        if (retryAfterMilliseconds == null)
        {
            throw new IllegalStateException("Redis did not return a rate-limit result");
        }

        if (retryAfterMilliseconds > 0)
        {
            throw new RateLimitExceededException(
                Duration.ofMillis(retryAfterMilliseconds)
            );
        }
    }

    private static String key(String operation, String scope, String value)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Rate-limit identity is required");
        }

        return KEY_PREFIX
            + operation
            + ":"
            + scope
            + ":"
            + value;
    }

    private record Limit(String key, int maximumRequests)
    {
    }
}
