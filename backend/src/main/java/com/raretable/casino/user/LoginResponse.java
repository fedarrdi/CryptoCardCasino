package com.raretable.casino.user;

import java.util.UUID;

public record LoginResponse(UUID userId, String name)
{
    public static LoginResponse from(User user)
    {
        return new LoginResponse(user.getUniqueId(), user.getName());
    }
}
