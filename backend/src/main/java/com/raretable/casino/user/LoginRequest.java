package com.raretable.casino.user;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String name)
{
}
