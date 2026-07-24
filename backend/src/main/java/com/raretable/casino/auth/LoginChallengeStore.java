package com.raretable.casino.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class LoginChallengeStore
{
    private final Map<String, LoginChallenge> challenges;
    private final Clock clock;

    public LoginChallengeStore(Clock clock)
    {
        this.challenges = new ConcurrentHashMap<>();
        this.clock = clock;
    }

    void save(LoginChallenge challenge)
    {
        LoginChallenge existingChallenge = challenges.putIfAbsent(challenge.nonce(), challenge);

        if (existingChallenge != null)
        {
            throw new IllegalStateException("Generated duplicate login nonce");
        }
    }

    LoginChallenge require(String nonce)
    {
        LoginChallenge challenge = challenges.get(nonce);

        if (challenge == null)
        {
            throw new WalletAuthenticationException("Login challenge does not exist");
        }

        return challenge;
    }

    void consume(LoginChallenge challenge)
    {
        if (!challenges.remove(challenge.nonce(), challenge))
        {
            throw new WalletAuthenticationException("Login challenge has already been used");
        }
    }

    void remove(LoginChallenge challenge)
    {
        challenges.remove(challenge.nonce(), challenge);
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void deleteExpiredChallenges()
    {
        Instant now = clock.instant();

        challenges.forEach((nonce, challenge) ->
        {
            if (!challenge.expiresAt().isAfter(now))
            {
                challenges.remove(nonce, challenge);
            }
        });
    }
}
