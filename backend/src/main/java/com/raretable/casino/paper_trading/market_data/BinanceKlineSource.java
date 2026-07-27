package com.raretable.casino.paper_trading.market_data;

import java.time.Instant;
import java.util.List;

public interface BinanceKlineSource
{
    int MAX_PAGE_SIZE = 1_000;

    List<BinanceKline> getBtcKlines(
        BtcCandleInterval interval,
        Instant startTime,
        int limit
    );
}
