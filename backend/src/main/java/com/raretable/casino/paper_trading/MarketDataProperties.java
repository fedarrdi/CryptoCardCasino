package com.raretable.casino.paper_trading;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("raretable.market-data")
public record MarketDataProperties(
    boolean enabled,
    URI restBaseUri,
    URI webSocketUri,
    Instant initialOpenTime,
    Duration reconciliationInterval,
    Duration reconnectDelay,
    int historyLimit
)
{
    public MarketDataProperties
    {
        requireScheme(restBaseUri, "https", "REST base URI");
        requireScheme(webSocketUri, "wss", "WebSocket URI");

        if (initialOpenTime == null)
        {
            throw new IllegalArgumentException(
                "Market-data initial open time is required"
            );
        }
        if (!initialOpenTime.equals(
            initialOpenTime.truncatedTo(ChronoUnit.HOURS)
        ))
        {
            throw new IllegalArgumentException(
                "Market-data initial open time must be hour-aligned"
            );
        }

        requirePositive(reconciliationInterval, "Reconciliation interval");
        requirePositive(reconnectDelay, "Reconnect delay");

        if (historyLimit <= 0
            || historyLimit > CandleHistoryQuery.MAX_PAGE_SIZE)
        {
            throw new IllegalArgumentException(
                "Market-data history limit must be between 1 and "
                    + CandleHistoryQuery.MAX_PAGE_SIZE
            );
        }
    }

    private static void requireScheme(URI uri, String scheme, String label)
    {
        if (uri == null
            || !uri.isAbsolute()
            || !scheme.equalsIgnoreCase(uri.getScheme()))
        {
            throw new IllegalArgumentException(label + " must use " + scheme);
        }
    }

    private static void requirePositive(Duration duration, String label)
    {
        if (duration == null || duration.isZero() || duration.isNegative())
        {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }
}
