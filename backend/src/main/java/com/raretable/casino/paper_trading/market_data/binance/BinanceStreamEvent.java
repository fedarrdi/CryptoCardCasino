package com.raretable.casino.paper_trading.market_data.binance;

import java.math.BigDecimal;
import java.time.Instant;

import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;
import com.raretable.casino.paper_trading.price.BtcQuote;

sealed interface BinanceStreamEvent
    permits BinanceCandleStreamEvent,
        BinanceTradeStreamEvent,
        BinanceBookTickerStreamEvent,
        BinanceMarkPriceStreamEvent
{
}

record BinanceCandleStreamEvent(LiveBtcCandle candle)
    implements BinanceStreamEvent
{
}

record BinanceTradeStreamEvent(
    long aggregateTradeId,
    BigDecimal price,
    Instant observedAt
)
    implements BinanceStreamEvent
{
}

record BinanceBookTickerStreamEvent(
    long updateId,
    BtcQuote quote,
    Instant observedAt
)
    implements BinanceStreamEvent
{
}

record BinanceMarkPriceStreamEvent(
    BigDecimal markPrice,
    BigDecimal indexPrice,
    BigDecimal fundingRate,
    Instant nextFundingTime,
    Instant observedAt
)
    implements BinanceStreamEvent
{
}
