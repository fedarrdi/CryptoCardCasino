package com.raretable.casino.paper_trading.price;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.raretable.casino.paper_trading.market_data.MarketDataSynchronizingException;

@Component
public class BinanceWrapper
{
    private final AtomicReference<BtcQuote> liveQuote =
        new AtomicReference<>();

    public BigDecimal getBtcPrice()
    {
        return getBtcQuote().midpoint();
    }

    public BtcQuote getBtcQuote()
    {
        BtcQuote quote = liveQuote.get();
        if (quote == null)
        {
            throw new MarketDataSynchronizingException(
                "Live BTC bid/ask is still synchronizing"
            );
        }
        return quote;
    }

    public void updateBtcQuote(BtcQuote quote)
    {
        if (quote == null)
        {
            throw new IllegalArgumentException("BTC quote is required");
        }
        liveQuote.set(quote);
    }

    public void clearBtcQuote()
    {
        liveQuote.set(null);
    }
}
