package com.raretable.casino.user;

import org.springframework.stereotype.Component;

import com.raretable.casino.user.persistence.UserEntity;

@Component
public final class UserMapper
{
    public User toDomain(UserEntity entity)
    {
        return new User(
            entity.getId(),
            entity.getName(),
            entity.getWalletAddress()
        );
    }
}
