package com.raretable.casino.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.raretable.casino.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public final class WalletSessionService
{
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    public WalletSessionService(
        SecurityContextRepository securityContextRepository,
        SessionAuthenticationStrategy sessionAuthenticationStrategy
    )
    {
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    }

    public void authenticate(
        User user,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        if (user.getWalletAddress().isEmpty())
        {
            throw new IllegalStateException("Authenticated user has no wallet address");
        }

        WalletPrincipal principal = new WalletPrincipal(user.getUniqueId());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            List.of()
        );

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext securityContext = securityContextHolderStrategy.createEmptyContext();
        securityContext.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);
    }
}
