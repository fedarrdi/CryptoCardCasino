package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class SiwePropertiesTests
{
    @Test
    void rejectsHttpOriginWithoutExplicitDevelopmentOverride()
    {
        assertThrows(
            IllegalArgumentException.class,
            () -> new SiweProperties(
                "localhost:5173",
                URI.create("http://localhost:5173"),
                Duration.ofMinutes(5),
                1,
                false
            )
        );
    }
}
