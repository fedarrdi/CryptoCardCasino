package com.raretable.casino.paper_trading;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public BtcCandlesResponse getBtcCandles()
    {
        return candleHistory.getBtcCandles();
    }
}
