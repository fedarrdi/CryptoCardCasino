package com.raretable.casino.user;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.raretable.casino.user.persistence.UserJpaRepository;

@Service
public final class UserService
{
    private static final Pattern WALLET_ADDRESS_PATTERN =
        Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final UserJpaRepository repository;
    private final UserMapper mapper;

    public UserService(UserJpaRepository repository, UserMapper mapper)
    {
        this.repository = repository;
        this.mapper = mapper;
    }

    public User findOrCreateByWalletAddress(String walletAddress)
    {
        if (walletAddress == null
            || !WALLET_ADDRESS_PATTERN.matcher(walletAddress).matches())
        {
            throw new IllegalArgumentException("Invalid Ethereum wallet address");
        }

        return repository.findByWalletAddressIgnoreCase(walletAddress)
            .map(mapper::toDomain)
            .orElseGet(() -> createUser(walletAddress));
    }

    public User getUser(UUID userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("User id is required");
        }

        return repository.findById(userId)
            .map(mapper::toDomain)
            .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private User createUser(String walletAddress)
    {
        repository.insertIfAbsent(
            UUID.randomUUID(),
            walletAddress,
            createDefaultName(walletAddress)
        );

        return repository.findByWalletAddressIgnoreCase(walletAddress)
            .map(mapper::toDomain)
            .orElseThrow(() -> new IllegalStateException(
                "User was not available after database insertion"
            ));
    }

    private static String createDefaultName(String walletAddress)
    {
        return "Player " + walletAddress.substring(2, 8);
    }
}
