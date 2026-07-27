package com.raretable.casino.paper_trading;

import java.util.List;

public record BtcCandlesResponse(
    String symbol,
    String interval,
    List<BtcCandle> candles,
    boolean hasMore,
    Long nextBefore
) {}
