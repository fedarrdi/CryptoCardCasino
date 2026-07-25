package com.raretable.casino.auth;

import java.util.UUID;

import com.raretable.casino.user.User;

public record AuthenticatedUserResponse(
    UUID userId,
    String name,
    String walletAddress
)
{
    public static AuthenticatedUserResponse from(User user)
    {
        String walletAddress = user.getWalletAddress()
            .orElseThrow(() -> new IllegalStateException("Authenticated user has no wallet address"));

        return new AuthenticatedUserResponse(
            user.getUniqueId(),
            user.getName(),
            walletAddress
        );
    }
}
