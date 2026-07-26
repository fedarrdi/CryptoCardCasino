package com.raretable.casino.auth;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    SiweProperties.class,
    AuthRateLimitProperties.class
})
public class AuthConfiguration
{
    @Bean
    public Clock authClock()
    {
        return Clock.systemUTC();
    }
}
