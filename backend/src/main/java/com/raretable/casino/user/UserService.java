package com.raretable.casino.user;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public final class UserService
{
    private final Map<UUID, User> users;

    public UserService()
    {
        this.users = new ConcurrentHashMap<>();
    }

    public User login(String name)
    {
        User user = new User(name);
        users.put(user.getUniqueId(), user);
        return user;
    }

    public User getUser(UUID userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("User id is required");
        }

        User user = users.get(userId);

        if (user == null)
        {
            throw new UserNotFoundException(userId);
        }

        return user;
    }
}
