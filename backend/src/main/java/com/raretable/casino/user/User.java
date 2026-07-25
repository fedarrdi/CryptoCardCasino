package com.raretable.casino.user;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class User
{
    private final UUID uniqueId;
    private final String walletAddress;
    private String name;

    public User(String name)
    {
        this(name, null);
    }

    User(String name, String walletAddress)
    {
        this(UUID.randomUUID(), name, walletAddress);
    }

    User(UUID uniqueId, String name, String walletAddress)
    {
        this.uniqueId = Objects.requireNonNull(uniqueId, "User id is required");
        validateName(name);
        this.walletAddress = walletAddress;
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

    public Optional<String> getWalletAddress()
    {
        return Optional.ofNullable(walletAddress);
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
