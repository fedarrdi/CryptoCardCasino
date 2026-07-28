package com.raretable.casino.paper_trading.market_data;

import java.time.Instant;
import java.util.List;

public interface BinanceFundingRateSource
{
    int MAX_PAGE_SIZE = 1000;

    List<BinanceFundingRate> getBtcFundingRates(
        Instant startTime,
        Instant endTime,
        int limit
    );
}
