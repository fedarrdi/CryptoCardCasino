package com.raretable.casino.paper_trading;

import java.math.BigDecimal;

public record BtcPriceResponse(
    String symbol,
    BigDecimal price
) {}
