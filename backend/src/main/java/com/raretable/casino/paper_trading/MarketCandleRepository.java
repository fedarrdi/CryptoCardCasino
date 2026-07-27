package com.raretable.casino.paper_trading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface MarketCandleRepository
{
    Optional<Instant> findLatestOpenTime(String symbol, String interval);

    List<StoredCandle> findLatest(String symbol, String interval, int limit);

    void upsertAll(List<StoredCandle> candles);
}
