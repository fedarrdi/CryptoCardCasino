package com.raretable.casino.paper_trading.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;
import com.raretable.casino.paper_trading.market_data.CandleHistoryQuery;
import com.raretable.casino.paper_trading.price.BinanceWrapper;

@RestController
@RequestMapping("/api/paper-trading")
public final class PaperTradingController
{
    private static final String BTC_USDT_SYMBOL = "BTCUSDT";

    private final BinanceWrapper binanceWrapper;
    private final CandleHistoryQuery candleHistory;

    public PaperTradingController(
        BinanceWrapper binanceWrapper,
        CandleHistoryQuery candleHistory
    )
    {
        this.binanceWrapper = binanceWrapper;
        this.candleHistory = candleHistory;
    }

    @GetMapping("/btc-price")
    public BtcPriceResponse getBtcPrice()
    {
        return new BtcPriceResponse(
            BTC_USDT_SYMBOL,
            binanceWrapper.getBtcPrice()
        );
    }

    @GetMapping("/btc-candles")
    public BtcCandlesResponse getBtcCandles(
        @RequestParam(name = "interval", defaultValue = "1h")
        String interval,
        @RequestParam(name = "before", required = false)
        @Positive
        Long before,
        @RequestParam(name = "limit", required = false)
        @Min(1)
        @Max(CandleHistoryQuery.MAX_PAGE_SIZE)
        Integer limit
    )
    {
        return candleHistory.getBtcCandles(
            BtcCandleInterval.parse(interval),
            before,
            limit
        );
    }
}
