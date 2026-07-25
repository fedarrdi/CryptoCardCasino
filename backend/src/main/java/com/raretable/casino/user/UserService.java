package com.raretable.casino.user;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public final class UserService
{
    private final Map<UUID, User> users;
    private final Map<String, User> usersByWalletAddress;

    public UserService()
    {
        this.users = new ConcurrentHashMap<>();
        this.usersByWalletAddress = new ConcurrentHashMap<>();
    }

    public User findOrCreateByWalletAddress(String walletAddress)
    {
        if (walletAddress == null || !walletAddress.matches("^0x[0-9a-fA-F]{40}$"))
        {
            throw new IllegalArgumentException("Invalid Ethereum wallet address");
        }

        String normalizedAddress = walletAddress.toLowerCase(Locale.ROOT);

        return usersByWalletAddress.computeIfAbsent(normalizedAddress, ignored ->
        {
            User user = new User(createDefaultName(walletAddress), walletAddress);
            users.put(user.getUniqueId(), user);
            return user;
        });
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

    private static String createDefaultName(String walletAddress)
    {
        return "Player " + walletAddress.substring(2, 8);
    }
}
