package com.raretable.casino.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.FilterChain;

class AbsoluteSessionLifetimeFilterTests
{
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(8);

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsSessionBeforeAbsoluteTimeout() throws Exception
    {
        MockHttpSession session = authenticatedSession(NOW.minus(Duration.ofHours(7)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();

        createFilter().doFilter(
            request("GET", "/api/auth/me", session),
            response,
            recordingFilterChain(filterChainCalled)
        );

        assertTrue(filterChainCalled.get());
        assertFalse(session.isInvalid());
        assertEquals(200, response.getStatus());
    }

    @Test
    void invalidatesSessionAtAbsoluteTimeout() throws Exception
    {
        MockHttpSession session = authenticatedSession(NOW.minus(ABSOLUTE_TIMEOUT));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();

        createFilter().doFilter(
            request("GET", "/api/auth/me", session),
            response,
            recordingFilterChain(filterChainCalled)
        );

        assertFalse(filterChainCalled.get());
        assertTrue(session.isInvalid());
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsAuthenticatedSessionWithoutLoginTimestamp() throws Exception
    {
        setWalletAuthentication();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();

        createFilter().doFilter(
            request("GET", "/api/auth/me", session),
            response,
            recordingFilterChain(filterChainCalled)
        );

        assertFalse(filterChainCalled.get());
        assertTrue(session.isInvalid());
        assertEquals(401, response.getStatus());
    }

    @Test
    void allowsExpiredClientToCreateNewSession() throws Exception
    {
        MockHttpSession session = authenticatedSession(NOW.minus(ABSOLUTE_TIMEOUT));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean filterChainCalled = new AtomicBoolean();

        createFilter().doFilter(
            request("POST", "/api/auth/sessions", session),
            response,
            recordingFilterChain(filterChainCalled)
        );

        assertTrue(filterChainCalled.get());
        assertFalse(session.isInvalid());
        assertEquals(200, response.getStatus());
    }

    private static AbsoluteSessionLifetimeFilter createFilter()
    {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        WalletSessionProperties properties = new WalletSessionProperties(ABSOLUTE_TIMEOUT);
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
            response.setStatus(401);

        return new AbsoluteSessionLifetimeFilter(clock, properties, entryPoint);
    }

    private static MockHttpSession authenticatedSession(Instant authenticatedAt)
    {
        setWalletAuthentication();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
            WalletSessionService.AUTHENTICATED_AT_SESSION_ATTRIBUTE,
            authenticatedAt
        );
        return session;
    }

    private static void setWalletAuthentication()
    {
        WalletPrincipal principal = new WalletPrincipal(UUID.randomUUID());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            List.of()
        ));
        SecurityContextHolder.setContext(context);
    }

    private static MockHttpServletRequest request(
        String method,
        String servletPath,
        MockHttpSession session
    )
    {
        MockHttpServletRequest request = new MockHttpServletRequest(method, servletPath);
        request.setServletPath(servletPath);
        request.setSession(session);
        return request;
    }

    private static FilterChain recordingFilterChain(AtomicBoolean called)
    {
        return (request, response) -> called.set(true);
    }
}
