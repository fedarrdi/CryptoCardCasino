package com.raretable.casino.paper_trading.market_data.binance;

import java.math.BigDecimal;
import java.time.Instant;

import com.raretable.casino.paper_trading.market_data.LiveBtcCandle;
import com.raretable.casino.paper_trading.price.BtcQuote;

sealed interface BinanceStreamEvent
    permits BinanceCandleStreamEvent,
        BinanceTradeStreamEvent,
        BinanceBookTickerStreamEvent
{
}

record BinanceCandleStreamEvent(LiveBtcCandle candle)
    implements BinanceStreamEvent
{
}

record BinanceTradeStreamEvent(BigDecimal price, Instant observedAt)
    implements BinanceStreamEvent
{
}

record BinanceBookTickerStreamEvent(BtcQuote quote)
    implements BinanceStreamEvent
{
}
