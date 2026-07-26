package com.raretable.casino.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.security.WalletPrincipal;
import com.raretable.casino.security.WalletSessionService;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public final class AuthController
{
    private final WalletAuthService walletAuthService;
    private final WalletSessionService walletSessionService;
    private final UserService userService;

    public AuthController(
        WalletAuthService walletAuthService,
        WalletSessionService walletSessionService,
        UserService userService
    )
    {
        this.walletAuthService = walletAuthService;
        this.walletSessionService = walletSessionService;
        this.userService = userService;
    }

    @PostMapping("/challenges")
    public ChallengeResponse createChallenge(
        @Valid @RequestBody ChallengeRequest request,
        HttpServletRequest httpRequest
    )
    {
        return walletAuthService.createChallenge(
            request.walletAddress(),
            request.chainId(),
            httpRequest.getRemoteAddr()
        );
    }

    @PostMapping("/sessions")
    public AuthenticatedUserResponse createSession(
        @Valid @RequestBody VerifySignatureRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    )
    {
        User user = walletAuthService.verify(
            request.nonce(),
            request.signature(),
            httpRequest.getRemoteAddr()
        );
        walletSessionService.authenticate(user, httpRequest, httpResponse);
        return AuthenticatedUserResponse.from(user);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse getCurrentUser(
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        User user = userService.getUser(principal.userId());
        return AuthenticatedUserResponse.from(user);
    }

    @GetMapping("/csrf")
    public CsrfToken getCsrfToken(CsrfToken csrfToken)
    {
        return csrfToken;
    }
}
