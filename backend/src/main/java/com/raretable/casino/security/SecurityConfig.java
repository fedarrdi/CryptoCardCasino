package com.raretable.casino.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(WalletSessionProperties.class)
public class SecurityConfig
{
    @Bean
    public SecurityContextRepository securityContextRepository()
    {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy()
    {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public SecurityFilterChain authSecurityFilterChain(
        HttpSecurity http,
        SecurityContextRepository securityContextRepository,
        ApiAuthenticationEntryPoint authenticationEntryPoint,
        ApiAccessDeniedHandler accessDeniedHandler,
        WalletSessionProperties sessionProperties,
        Clock clock
    ) throws Exception
    {
        AbsoluteSessionLifetimeFilter absoluteSessionLifetimeFilter =
            new AbsoluteSessionLifetimeFilter(
                clock,
                sessionProperties,
                authenticationEntryPoint
            );

        http
            .securityMatcher("/api/**", "/ws/**")
            .addFilterAfter(absoluteSessionLifetimeFilter, SecurityContextHolderFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, "/api/auth/challenges", "/api/auth/sessions")
                .permitAll()
                .anyRequest()
                .authenticated()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/auth/challenges", "/api/auth/sessions")
            )
            .securityContext(securityContext -> securityContext
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true)
            )
            .requestCache(requestCache -> requestCache
                .requestCache(new NullRequestCache())
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value())
                )
            );

        return http.build();
    }
}
