package com.raretable.casino.user.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID>
{
    Optional<UserEntity> findByWalletAddressIgnoreCase(String walletAddress);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO users (id, wallet_address, name)
        VALUES (:id, :walletAddress, :name)
        ON CONFLICT (LOWER(wallet_address)) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("walletAddress") String walletAddress,
        @Param("name") String name
    );
}
