package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(properties = {
    "raretable.auth.siwe.domain=raretable.example",
    "raretable.auth.siwe.uri=https://raretable.example"
})
class ProductionConfigurationTests
{
    @Autowired
    private SiweProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void requiresHttpsAndSecureSessionCookiesByDefault()
    {
        assertEquals("https://raretable.example", properties.uri().toString());
        assertFalse(properties.allowInsecureOrigin());
        assertTrue(environment.getProperty(
            "server.servlet.session.cookie.secure",
            Boolean.class,
            false
        ));
    }
}
