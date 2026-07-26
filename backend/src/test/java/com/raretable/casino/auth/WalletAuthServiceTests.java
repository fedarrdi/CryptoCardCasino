package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import com.raretable.casino.InfrastructureTestConfiguration;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Import(InfrastructureTestConfiguration.class)
class WalletAuthServiceTests
{
    private static final ECKeyPair WALLET = ECKeyPair.create(BigInteger.ONE);
    private static final ECKeyPair OTHER_WALLET = ECKeyPair.create(BigInteger.TWO);
    private static final String REQUEST_SOURCE = "203.0.113.1";

    private MutableClock clock;
    private WalletAuthService authService;
    private LoginChallengeStore challengeStore;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp()
    {
        clock = new MutableClock(Instant.parse("2026-07-24T10:00:00Z"));
        SiweProperties properties = new SiweProperties(
            "localhost:5173",
            URI.create("http://localhost:5173"),
            Duration.ofMinutes(5),
            1,
            true
        );
        challengeStore = new LoginChallengeStore(
            redis,
            jsonMapper,
            clock
        );
        EthereumSignatureVerifier signatureVerifier = new EthereumSignatureVerifier();
        AuthRateLimitProperties.Limits limits = new AuthRateLimitProperties.Limits(
            1000,
            1000,
            1000
        );
        AuthenticationRateLimiter rateLimiter = new AuthenticationRateLimiter(
            redis,
            new AuthRateLimitProperties(Duration.ofMinutes(1), limits, limits)
        );

        authService = new WalletAuthService(
            challengeStore,
            new SecureNonceGenerator(),
            new SiweMessageFactory(properties),
            signatureVerifier,
            properties,
            rateLimiter,
            userService,
            clock
        );
    }

    @Test
    void validSignatureCreatesUserForWallet()
    {
        String walletAddress = getAddress(WALLET);
        ChallengeResponse challenge = authService.createChallenge(
            walletAddress,
            1,
            REQUEST_SOURCE
        );

        User user = authService.verify(
            challenge.nonce(),
            sign(challenge.message(), WALLET),
            REQUEST_SOURCE
        );

        assertEquals(walletAddress, user.getWalletAddress().orElseThrow());
        assertTrue(challenge.message().startsWith(
            "http://localhost:5173 wants you to sign in with your Ethereum account:\n"
                + walletAddress
        ));
        assertTrue(challenge.message().contains("Nonce: " + challenge.nonce()));
        assertTrue(challenge.message().contains("Chain ID: 1"));
    }

    @Test
    void repeatedLoginForSameWalletReturnsSameUser()
    {
        String walletAddress = getAddress(WALLET);
        User firstLogin = authenticate(walletAddress, WALLET);
        User secondLogin = authenticate(walletAddress, WALLET);

        assertEquals(firstLogin.getUniqueId(), secondLogin.getUniqueId());
    }

    @Test
    void challengeCannotBeUsedTwice()
    {
        ChallengeResponse challenge = authService.createChallenge(
            getAddress(WALLET),
            1,
            REQUEST_SOURCE
        );
        String signature = sign(challenge.message(), WALLET);

        authService.verify(challenge.nonce(), signature, REQUEST_SOURCE);

        assertThrows(
            WalletAuthenticationException.class,
            () -> authService.verify(challenge.nonce(), signature, REQUEST_SOURCE)
        );
    }

    @Test
    void anotherStoreInstanceCanConsumeChallengeBeforeItsRedisTtl()
    {
        ChallengeResponse response = authService.createChallenge(
            getAddress(WALLET),
            1,
            REQUEST_SOURCE
        );
        Long timeToLive = redis.getExpire(
            "raretable:auth:challenge:" + response.nonce(),
            TimeUnit.SECONDS
        );
        LoginChallengeStore anotherStore = new LoginChallengeStore(
            redis,
            jsonMapper,
            clock
        );

        LoginChallenge challenge = anotherStore.take(response.nonce());

        assertEquals(response.nonce(), challenge.nonce());
        assertTrue(timeToLive > 0 && timeToLive <= Duration.ofMinutes(5).toSeconds());
        assertThrows(
            WalletAuthenticationException.class,
            () -> challengeStore.take(response.nonce())
        );
    }

    @Test
    void wrongWalletSignatureConsumesChallenge()
    {
        ChallengeResponse challenge = authService.createChallenge(
            getAddress(WALLET),
            1,
            REQUEST_SOURCE
        );

        assertThrows(
            WalletAuthenticationException.class,
            () -> authService.verify(
                challenge.nonce(),
                sign(challenge.message(), OTHER_WALLET),
                REQUEST_SOURCE
            )
        );

        assertThrows(
            WalletAuthenticationException.class,
            () -> authService.verify(
                challenge.nonce(),
                sign(challenge.message(), WALLET),
                REQUEST_SOURCE
            )
        );
    }

    @Test
    void expiredChallengeIsRejected()
    {
        ChallengeResponse challenge = authService.createChallenge(
            getAddress(WALLET),
            1,
            REQUEST_SOURCE
        );
        clock.advance(Duration.ofMinutes(5));

        assertThrows(
            WalletAuthenticationException.class,
            () -> authService.verify(
                challenge.nonce(),
                sign(challenge.message(), WALLET),
                REQUEST_SOURCE
            )
        );
    }

    @Test
    void unsupportedChainIsRejected()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> authService.createChallenge(
                getAddress(WALLET),
                11155111,
                REQUEST_SOURCE
            )
        );
    }

    private User authenticate(String walletAddress, ECKeyPair wallet)
    {
        ChallengeResponse challenge = authService.createChallenge(
            walletAddress,
            1,
            REQUEST_SOURCE
        );
        return authService.verify(
            challenge.nonce(),
            sign(challenge.message(), wallet),
            REQUEST_SOURCE
        );
    }

    private static String getAddress(ECKeyPair wallet)
    {
        return Keys.toChecksumAddress(
            Numeric.prependHexPrefix(Keys.getAddress(wallet.getPublicKey()))
        );
    }

    private static String sign(String message, ECKeyPair wallet)
    {
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(
            message.getBytes(StandardCharsets.UTF_8),
            wallet
        );
        byte[] signature = new byte[65];

        System.arraycopy(signatureData.getR(), 0, signature, 0, 32);
        System.arraycopy(signatureData.getS(), 0, signature, 32, 32);
        System.arraycopy(signatureData.getV(), 0, signature, 64, 1);

        return Numeric.toHexString(signature);
    }

    private static final class MutableClock extends Clock
    {
        private Instant instant;

        private MutableClock(Instant instant)
        {
            this.instant = instant;
        }

        void advance(Duration duration)
        {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            if (!zone.equals(getZone()))
            {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }

            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }
}
