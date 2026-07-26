package com.raretable.casino.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@Service
public final class WalletAuthService
{
    private final LoginChallengeStore challengeStore;
    private final SecureNonceGenerator nonceGenerator;
    private final SiweMessageFactory messageFactory;
    private final EthereumSignatureVerifier signatureVerifier;
    private final SiweProperties properties;
    private final AuthenticationRateLimiter rateLimiter;
    private final UserService userService;
    private final Clock clock;

    public WalletAuthService(
        LoginChallengeStore challengeStore,
        SecureNonceGenerator nonceGenerator,
        SiweMessageFactory messageFactory,
        EthereumSignatureVerifier signatureVerifier,
        SiweProperties properties,
        AuthenticationRateLimiter rateLimiter,
        UserService userService,
        Clock clock
    )
    {
        this.challengeStore = challengeStore;
        this.nonceGenerator = nonceGenerator;
        this.messageFactory = messageFactory;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.userService = userService;
        this.clock = clock;
    }

    public ChallengeResponse createChallenge(
        String walletAddress,
        long chainId,
        String requestSource
    )
    {
        if (chainId != properties.chainId())
        {
            throw new IllegalArgumentException("Unsupported Ethereum chain id: " + chainId);
        }

        String normalizedAddress = signatureVerifier.normalizeAddress(walletAddress);
        rateLimiter.reserveChallengeRequest(requestSource, normalizedAddress);
        String nonce = nonceGenerator.generate();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.challengeTtl());
        String message = messageFactory.create(
            normalizedAddress,
            chainId,
            nonce,
            issuedAt,
            expiresAt
        );
        LoginChallenge challenge = new LoginChallenge(
            nonce,
            normalizedAddress,
            message,
            expiresAt
        );

        challengeStore.save(challenge);
        return new ChallengeResponse(nonce, message, expiresAt);
    }

    public User verify(String nonce, String signature, String requestSource)
    {
        rateLimiter.reserveVerificationRequest(requestSource);
        LoginChallenge challenge = challengeStore.take(nonce);
        Instant now = clock.instant();

        if (!challenge.expiresAt().isAfter(now))
        {
            throw new WalletAuthenticationException("Login challenge has expired");
        }

        rateLimiter.reserveWalletVerification(challenge.walletAddress());
        String recoveredAddress = signatureVerifier.recoverAddress(
            challenge.message(),
            signature
        );

        if (!recoveredAddress.equalsIgnoreCase(challenge.walletAddress()))
        {
            throw new WalletAuthenticationException("Signature does not match the requested wallet");
        }

        return userService.findOrCreateByWalletAddress(challenge.walletAddress());
    }
}
