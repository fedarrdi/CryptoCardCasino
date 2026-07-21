package com.raretable.casino.user;

import java.util.UUID;

public final class UserNotFoundException extends RuntimeException
{
    public UserNotFoundException(UUID userId)
    {
        super("User not found: " + userId);
    }
}
