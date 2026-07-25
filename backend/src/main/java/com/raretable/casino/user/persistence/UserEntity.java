package com.raretable.casino.user.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity
{
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "wallet_address", nullable = false, updatable = false, length = 42)
    private String walletAddress;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected UserEntity()
    {
    }

    public UUID getId()
    {
        return id;
    }

    public String getWalletAddress()
    {
        return walletAddress;
    }

    public String getName()
    {
        return name;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }
}
