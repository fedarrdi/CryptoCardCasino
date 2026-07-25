package com.raretable.casino.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifySignatureRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9]{8,128}$")
    String nonce,

    @NotBlank
    String signature
)
{
}
