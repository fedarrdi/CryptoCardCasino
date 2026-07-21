package com.raretable.casino.Users;

import java.util.Objects;
import java.util.UUID;

public class User
{
    private final UUID uniqueId;
    private String name;

    public User(String name)
    {
        this.uniqueId = UUID.randomUUID();
        this.name = name;
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
        this.name = name;
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
}
