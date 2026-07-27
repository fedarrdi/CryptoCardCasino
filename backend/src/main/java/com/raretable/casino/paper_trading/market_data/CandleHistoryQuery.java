package com.raretable.casino.paper_trading.market_data;

import com.raretable.casino.paper_trading.api.BtcCandlesResponse;

public interface CandleHistoryQuery
{
    int MAX_PAGE_SIZE = 2_000;

    BtcCandlesResponse getBtcCandles(
        BtcCandleInterval interval,
        Long before,
        Integer limit
    );

    default BtcCandlesResponse getBtcCandles()
    {
        return getBtcCandles(BtcCandleInterval.ONE_HOUR, null, null);
    }

    default BtcCandlesResponse getBtcCandles(Long before, Integer limit)
    {
        return getBtcCandles(BtcCandleInterval.ONE_HOUR, before, limit);
    }
}
