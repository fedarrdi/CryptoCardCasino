package com.raretable.casino.paper_trading.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;

class BinanceWrapperTests
{
    @Test
    void servesAndClearsTheLatestLiveBookQuote()
    {
        BinanceWrapper binance = new BinanceWrapper();
        assertThrows(
            MarketDataSynchronizingException.class,
            binance::getBtcQuote
        );

        binance.updateBtcQuote(new BtcQuote(
            new BigDecimal("65000.10000000"),
            new BigDecimal("65000.30000000")
        ));

        assertEquals(
            new BigDecimal("65000.20000000"),
            binance.getBtcPrice()
        );

        binance.clearBtcQuote();
        assertThrows(
            MarketDataSynchronizingException.class,
            binance::getBtcQuote
        );
    }
}
