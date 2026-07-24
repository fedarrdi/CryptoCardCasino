package com.raretable.casino.security;

import java.io.Serializable;
import java.util.UUID;

public record WalletPrincipal(
    UUID userId
) implements Serializable
{
}
