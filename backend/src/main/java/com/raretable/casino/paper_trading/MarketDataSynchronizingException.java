package com.raretable.casino.paper_trading;

public final class MarketDataSynchronizingException extends RuntimeException
{
    public MarketDataSynchronizingException()
    {
        super("BTC candle history is still synchronizing");
    }
}
