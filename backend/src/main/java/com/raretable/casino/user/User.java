package com.raretable.casino.user;

import java.util.Objects;
import java.util.UUID;

public final class User
{
    private final UUID uniqueId;
    private String name;

    public User(String name)
    {
        validateName(name);
        this.uniqueId = UUID.randomUUID();
        this.name = name.trim();
    }

    public UUID getUniqueId()
    {
        return uniqueId;
    }

    public String getName()
    {
        return name;
    }

    public void rename(String name)
    {
        validateName(name);
        this.name = name.trim();
    }

    @Override
    public boolean equals(Object object)
    {
        if (this == object)
        {
            return true;
        }

        if (!(object instanceof User user))
        {
            return false;
        }

        return uniqueId.equals(user.uniqueId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(uniqueId);
    }

    private static void validateName(String name)
    {
        if (name == null || name.isBlank())
        {
            throw new IllegalArgumentException("User name is required");
        }
    }
}
