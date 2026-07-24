package com.raretable.casino.table;

import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

final class TestUserFactory
{
    private TestUserFactory()
    {
    }

    static User createWalletUser(UserService userService, int addressValue)
    {
        String walletAddress = "0x%040x".formatted(addressValue);
        return userService.findOrCreateByWalletAddress(walletAddress);
    }
}
