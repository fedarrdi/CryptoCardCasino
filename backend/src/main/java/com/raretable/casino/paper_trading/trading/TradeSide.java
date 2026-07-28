package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;

import com.raretable.casino.paper_trading.price.BtcQuote;

public enum TradeSide
{
    LONG
    {
        @Override
        public BigDecimal openingPrice(BtcQuote quote)
        {
            return quote.askPrice();
        }

        @Override
        public BigDecimal closingPrice(BtcQuote quote)
        {
            return quote.bidPrice();
        }

        @Override
        BigDecimal rawPnl(
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal quantity
        )
        {
            return exitPrice.subtract(entryPrice).multiply(quantity);
        }

        @Override
        boolean stopLossTriggered(BigDecimal price, BigDecimal stopLoss)
        {
            return stopLoss != null && price.compareTo(stopLoss) <= 0;
        }

        @Override
        boolean takeProfitTriggered(BigDecimal price, BigDecimal takeProfit)
        {
            return takeProfit != null && price.compareTo(takeProfit) >= 0;
        }

        @Override
        public void validateRiskControls(
            BigDecimal triggerPrice,
            BigDecimal stopLoss,
            BigDecimal takeProfit
        )
        {
            if (
                stopLoss != null
                && stopLoss.compareTo(triggerPrice) >= 0
            )
            {
                throw new IllegalArgumentException(
                    "Long stop loss must be below the current last price"
                );
            }
            if (
                takeProfit != null
                && takeProfit.compareTo(triggerPrice) <= 0
            )
            {
                throw new IllegalArgumentException(
                    "Long take profit must be above the current last price"
                );
            }
        }
    },
    SHORT
    {
        @Override
        public BigDecimal openingPrice(BtcQuote quote)
        {
            return quote.bidPrice();
        }

        @Override
        public BigDecimal closingPrice(BtcQuote quote)
        {
            return quote.askPrice();
        }

        @Override
        BigDecimal rawPnl(
            BigDecimal entryPrice,
            BigDecimal exitPrice,
            BigDecimal quantity
        )
        {
            return entryPrice.subtract(exitPrice).multiply(quantity);
        }

        @Override
        boolean stopLossTriggered(BigDecimal price, BigDecimal stopLoss)
        {
            return stopLoss != null && price.compareTo(stopLoss) >= 0;
        }

        @Override
        boolean takeProfitTriggered(BigDecimal price, BigDecimal takeProfit)
        {
            return takeProfit != null && price.compareTo(takeProfit) <= 0;
        }

        @Override
        public void validateRiskControls(
            BigDecimal triggerPrice,
            BigDecimal stopLoss,
            BigDecimal takeProfit
        )
        {
            if (
                stopLoss != null
                && stopLoss.compareTo(triggerPrice) <= 0
            )
            {
                throw new IllegalArgumentException(
                    "Short stop loss must be above the current last price"
                );
            }
            if (
                takeProfit != null
                && takeProfit.compareTo(triggerPrice) >= 0
            )
            {
                throw new IllegalArgumentException(
                    "Short take profit must be below the current last price"
                );
            }
        }
    };

    public abstract BigDecimal openingPrice(BtcQuote quote);

    public abstract BigDecimal closingPrice(BtcQuote quote);

    abstract BigDecimal rawPnl(
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal quantity
    );

    abstract boolean stopLossTriggered(BigDecimal price, BigDecimal stopLoss);

    abstract boolean takeProfitTriggered(BigDecimal price, BigDecimal takeProfit);

    public abstract void validateRiskControls(
        BigDecimal triggerPrice,
        BigDecimal stopLoss,
        BigDecimal takeProfit
    );
}
