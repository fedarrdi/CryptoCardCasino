package com.raretable.casino.paper_trading;

public interface CandleHistoryQuery
{
    int MAX_PAGE_SIZE = 2_000;

    BtcCandlesResponse getBtcCandles(Long before, Integer limit);

    default BtcCandlesResponse getBtcCandles()
    {
        return getBtcCandles(null, null);
    }
}
