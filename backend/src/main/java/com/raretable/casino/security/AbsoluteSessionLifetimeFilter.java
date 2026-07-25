package com.raretable.casino.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter
{
    private final Clock clock;
    private final WalletSessionProperties properties;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    public AbsoluteSessionLifetimeFilter(
        Clock clock,
        WalletSessionProperties properties,
        AuthenticationEntryPoint authenticationEntryPoint
    )
    {
        this.clock = clock;
        this.properties = properties;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        if (!"POST".equals(request.getMethod()))
        {
            return false;
        }

        String path = request.getServletPath();
        return path.equals("/api/auth/challenges") || path.equals("/api/auth/sessions");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException
    {
        Authentication authentication = securityContextHolderStrategy
            .getContext()
            .getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || !(authentication.getPrincipal() instanceof WalletPrincipal))
        {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);

        if (session == null)
        {
            rejectExpiredSession(request, response);
            return;
        }

        Object value = session.getAttribute(
            WalletSessionService.AUTHENTICATED_AT_SESSION_ATTRIBUTE
        );

        if (!(value instanceof Instant authenticatedAt)
            || !authenticatedAt
                .plus(properties.absoluteTimeout())
                .isAfter(clock.instant()))
        {
            session.invalidate();
            rejectExpiredSession(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectExpiredSession(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws ServletException, IOException
    {
        securityContextHolderStrategy.clearContext();
        authenticationEntryPoint.commence(
            request,
            response,
            new InsufficientAuthenticationException("Session has expired")
        );
    }
}
