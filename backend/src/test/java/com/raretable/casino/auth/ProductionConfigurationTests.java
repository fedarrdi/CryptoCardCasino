package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.raretable.casino.PostgresTestConfiguration;
import com.raretable.casino.security.WalletSessionProperties;

@SpringBootTest(properties = {
    "raretable.auth.siwe.domain=raretable.example",
    "raretable.auth.siwe.uri=https://raretable.example"
})
@Import(PostgresTestConfiguration.class)
class ProductionConfigurationTests
{
    @Autowired
    private SiweProperties properties;

    @Autowired
    private Environment environment;

    @Autowired
    private WalletSessionProperties sessionProperties;

    @Test
    void requiresHttpsAndSecureSessionCookiesByDefault()
    {
        assertEquals("https://raretable.example", properties.uri().toString());
        assertEquals(Duration.ofHours(8), sessionProperties.absoluteTimeout());
        assertFalse(properties.allowInsecureOrigin());
        assertTrue(environment.getProperty(
            "server.servlet.session.cookie.secure",
            Boolean.class,
            false
        ));
    }
}
