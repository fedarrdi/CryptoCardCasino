package com.raretable.casino.paper_trading;

import java.time.Instant;
import java.util.List;

interface BinanceKlineSource
{
    List<BinanceKline> getBtcKlines(
        BtcCandleInterval interval,
        Instant startTime,
        int limit
    );
}
