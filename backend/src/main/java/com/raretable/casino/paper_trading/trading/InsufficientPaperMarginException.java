package com.raretable.casino.paper_trading.trading;

import java.math.BigDecimal;

public final class InsufficientPaperMarginException extends RuntimeException
{
    public InsufficientPaperMarginException(
        BigDecimal requestedMargin,
        BigDecimal availableMargin
    )
    {
        super(
            "Requested margin " + requestedMargin.toPlainString()
                + " USD exceeds available cross margin "
                + availableMargin.toPlainString()
                + " USD"
        );
    }
}
