package com.raretable.casino.auth;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("raretable.auth.siwe")
public record SiweProperties(
    String domain,
    URI uri,
    Duration challengeTtl,
    long chainId,
    boolean allowInsecureOrigin
)
{
    public SiweProperties
    {
        if (domain == null || domain.isBlank())
        {
            throw new IllegalArgumentException("SIWE domain is required");
        }

        if (uri == null || uri.getScheme() == null || uri.getAuthority() == null)
        {
            throw new IllegalArgumentException("SIWE URI must be absolute");
        }

        if (!domain.equalsIgnoreCase(uri.getAuthority()))
        {
            throw new IllegalArgumentException("SIWE domain must match the URI authority");
        }

        String scheme = uri.getScheme();

        if (!scheme.equalsIgnoreCase("https")
            && !(allowInsecureOrigin && scheme.equalsIgnoreCase("http")))
        {
            throw new IllegalArgumentException("SIWE URI must use HTTPS");
        }

        if (challengeTtl == null || challengeTtl.isZero() || challengeTtl.isNegative())
        {
            throw new IllegalArgumentException("SIWE challenge TTL must be positive");
        }

        if (chainId <= 0)
        {
            throw new IllegalArgumentException("SIWE chain id must be positive");
        }
    }
}
