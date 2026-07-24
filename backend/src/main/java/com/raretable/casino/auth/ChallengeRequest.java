package com.raretable.casino.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ChallengeRequest(
    @NotBlank String walletAddress,
    @Positive long chainId
)
{
}
