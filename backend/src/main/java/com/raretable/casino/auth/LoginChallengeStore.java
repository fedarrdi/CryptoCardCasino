package com.raretable.casino.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class LoginChallengeStore
{
    private static final String KEY_PREFIX = "raretable:auth:challenge:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public LoginChallengeStore(
        StringRedisTemplate redis,
        JsonMapper jsonMapper,
        Clock clock
    )
    {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    void save(LoginChallenge challenge)
    {
        Duration timeToLive = Duration.between(clock.instant(), challenge.expiresAt());

        if (timeToLive.isZero() || timeToLive.isNegative())
        {
            throw new IllegalArgumentException("Login challenge must expire in the future");
        }

        Boolean saved = redis.opsForValue().setIfAbsent(
            key(challenge.nonce()),
            serialize(challenge),
            timeToLive
        );

        if (!Boolean.TRUE.equals(saved))
        {
            throw new IllegalStateException("Generated duplicate login nonce");
        }
    }

    LoginChallenge take(String nonce)
    {
        String serializedChallenge = redis.opsForValue().getAndDelete(key(nonce));

        if (serializedChallenge == null)
        {
            throw new WalletAuthenticationException(
                "Login challenge does not exist, has expired, or has already been used"
            );
        }

        return deserialize(serializedChallenge);
    }

    private String serialize(LoginChallenge challenge)
    {
        try
        {
            return jsonMapper.writeValueAsString(challenge);
        }
        catch (JacksonException exception)
        {
            throw new IllegalStateException("Could not serialize login challenge", exception);
        }
    }

    private LoginChallenge deserialize(String serializedChallenge)
    {
        try
        {
            return jsonMapper.readValue(serializedChallenge, LoginChallenge.class);
        }
        catch (JacksonException exception)
        {
            throw new IllegalStateException("Could not deserialize login challenge", exception);
        }
    }

    private static String key(String nonce)
    {
        return KEY_PREFIX + nonce;
    }
}
