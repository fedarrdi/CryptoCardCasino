package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class DevProfileConfigurationTests
{
    @Autowired
    private SiweProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void allowsHttpOnlyInExplicitDevelopmentProfile()
    {
        assertEquals("http://localhost:5173", properties.uri().toString());
        assertTrue(properties.allowInsecureOrigin());
        assertFalse(environment.getProperty(
            "server.servlet.session.cookie.secure",
            Boolean.class,
            true
        ));
    }
}
