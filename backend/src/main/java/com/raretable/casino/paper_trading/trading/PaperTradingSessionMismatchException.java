package com.raretable.casino.paper_trading.trading;

public final class PaperTradingSessionMismatchException
    extends RuntimeException
{
    public PaperTradingSessionMismatchException()
    {
        super(
            "The active wallet session changed; refresh before trading"
        );
    }
}
