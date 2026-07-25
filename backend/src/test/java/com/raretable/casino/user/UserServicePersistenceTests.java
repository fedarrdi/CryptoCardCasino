package com.raretable.casino.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.raretable.casino.PostgresTestConfiguration;
import com.raretable.casino.user.persistence.UserEntity;
import com.raretable.casino.user.persistence.UserJpaRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class UserServicePersistenceTests
{
    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository repository;

    @Test
    void firstWalletLoginCreatesPersistentUser()
    {
        String walletAddress = "0xa123456789012345678901234567890123456789";

        User createdUser = userService.findOrCreateByWalletAddress(walletAddress);
        User loadedUser = userService.getUser(createdUser.getUniqueId());
        UserEntity storedUser = repository
            .findByWalletAddressIgnoreCase(walletAddress)
            .orElseThrow();

        assertEquals(createdUser, loadedUser);
        assertEquals(walletAddress, loadedUser.getWalletAddress().orElseThrow());
        assertEquals("Player a12345", loadedUser.getName());
        assertNotNull(storedUser.getCreatedAt());
    }

    @Test
    void walletLookupIsCaseInsensitive()
    {
        String lowercaseAddress = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        String uppercaseAddress = "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD";

        User firstLogin = userService.findOrCreateByWalletAddress(lowercaseAddress);
        User secondLogin = userService.findOrCreateByWalletAddress(uppercaseAddress);

        assertEquals(firstLogin.getUniqueId(), secondLogin.getUniqueId());
        assertEquals(
            lowercaseAddress,
            secondLogin.getWalletAddress().orElseThrow()
        );
    }

    @Test
    void simultaneousFirstLoginsCreateOneWalletUser() throws Exception
    {
        String walletAddress = "0xb123456789012345678901234567890123456789";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try
        {
            Future<User> firstLogin = executor.submit(() ->
            {
                start.await();
                return userService.findOrCreateByWalletAddress(walletAddress);
            });
            Future<User> secondLogin = executor.submit(() ->
            {
                start.await();
                return userService.findOrCreateByWalletAddress(walletAddress);
            });

            start.countDown();

            assertEquals(
                firstLogin.get().getUniqueId(),
                secondLogin.get().getUniqueId()
            );
        }
        finally
        {
            executor.shutdownNow();
        }
    }
}
