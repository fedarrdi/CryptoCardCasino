package com.raretable.casino.paper_trading.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;

class BinanceWrapperTests
{
    private static final Instant NOW =
        Instant.parse("2026-07-27T08:13:22Z");

    @Test
    void servesACompleteFreshPerpetualMarketSnapshot()
    {
        BinanceWrapper binance = wrapper();
        assertThrows(
            MarketDataSynchronizingException.class,
            binance::getBtcPerpetualMarketSnapshot
        );

        binance.updateBtcQuote(new BtcQuote(
            new BigDecimal("65000.10000000"),
            new BigDecimal("65000.30000000")
        ), NOW.minusSeconds(1));
        binance.updateLastPrice(
            new BigDecimal("65000.25000000"),
            NOW.minusSeconds(1)
        );
        binance.updateMarkPrice(
            new BigDecimal("65001.00000000"),
            new BigDecimal("64999.50000000"),
            new BigDecimal("0.00010000"),
            Instant.parse("2026-07-27T16:00:00Z"),
            NOW.minusSeconds(1)
        );

        assertEquals(
            new BigDecimal("65000.25000000"),
            binance.getBtcPrice()
        );
        BtcPerpetualMarketSnapshot snapshot =
            binance.getBtcPerpetualMarketSnapshot();
        assertEquals(new BigDecimal("65001.00000000"), snapshot.markPrice());
        assertEquals(new BigDecimal("64999.50000000"), snapshot.indexPrice());
        assertEquals(new BigDecimal("0.00010000"), snapshot.fundingRate());
        assertEquals(
            new BigDecimal("65000.10000000"),
            binance.getBtcQuote().bidPrice()
        );

        binance.clearBtcQuote();
        assertThrows(
            MarketDataSynchronizingException.class,
            binance::getBtcPerpetualMarketSnapshot
        );
    }

    @Test
    void refusesACompleteButStaleSnapshot()
    {
        BinanceWrapper binance = wrapper();
        Instant stale = NOW.minusSeconds(6);
        binance.updateLastPrice(new BigDecimal("65000"), stale);
        binance.updateBtcQuote(
            new BtcQuote(
                new BigDecimal("64999"),
                new BigDecimal("65001")
            ),
            stale
        );
        binance.updateMarkPrice(
            new BigDecimal("65000"),
            new BigDecimal("65000"),
            BigDecimal.ZERO,
            Instant.parse("2026-07-27T16:00:00Z"),
            stale
        );

        assertThrows(
            MarketDataSynchronizingException.class,
            binance::getBtcPerpetualMarketSnapshot
        );
    }

    private static BinanceWrapper wrapper()
    {
        return new BinanceWrapper(
            Duration.ofSeconds(5),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
