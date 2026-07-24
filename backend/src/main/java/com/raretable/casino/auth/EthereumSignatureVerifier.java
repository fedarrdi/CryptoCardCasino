package com.raretable.casino.auth;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Verifies EIP-191 personal signatures from externally owned accounts.
 * ERC-1271 contract-wallet signatures are not supported.
 */
@Component
public final class EthereumSignatureVerifier
{
    private static final int COMPONENT_BYTES = 32;

    public String normalizeAddress(String walletAddress)
    {
        if (walletAddress == null || !walletAddress.matches("^0x[0-9a-fA-F]{40}$"))
        {
            throw new IllegalArgumentException("Invalid Ethereum wallet address");
        }

        return Keys.toChecksumAddress(walletAddress);
    }

    public String recoverAddress(String message, String signature)
    {
        if (signature == null || !signature.matches("^0x[0-9a-fA-F]{130}$"))
        {
            throw new WalletAuthenticationException("Invalid wallet signature");
        }

        byte[] signatureBytes = Numeric.hexStringToByteArray(signature);

        int recoveryValue = Byte.toUnsignedInt(signatureBytes[64]);

        if (recoveryValue == 0 || recoveryValue == 1)
        {
            recoveryValue += 27;
        }

        if (recoveryValue != 27 && recoveryValue != 28)
        {
            throw new WalletAuthenticationException("Invalid wallet signature");
        }

        Sign.SignatureData signatureData = new Sign.SignatureData(
            (byte) recoveryValue,
            Arrays.copyOfRange(signatureBytes, 0, COMPONENT_BYTES),
            Arrays.copyOfRange(signatureBytes, COMPONENT_BYTES, COMPONENT_BYTES * 2)
        );

        try
        {
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(
                message.getBytes(StandardCharsets.UTF_8),
                signatureData
            );

            return Keys.toChecksumAddress(Numeric.prependHexPrefix(Keys.getAddress(publicKey)));
        }
        catch (SignatureException exception)
        {
            throw new WalletAuthenticationException("Invalid wallet signature", exception);
        }
    }
}
