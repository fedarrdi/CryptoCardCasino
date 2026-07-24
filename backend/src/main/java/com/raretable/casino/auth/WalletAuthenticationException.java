package com.raretable.casino.auth;

public final class WalletAuthenticationException extends RuntimeException
{
    public WalletAuthenticationException(String message)
    {
        super(message);
    }

    public WalletAuthenticationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
