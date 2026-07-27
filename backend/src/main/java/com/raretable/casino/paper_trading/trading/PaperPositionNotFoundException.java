package com.raretable.casino.paper_trading.trading;

import java.util.UUID;

public final class PaperPositionNotFoundException extends RuntimeException
{
    public PaperPositionNotFoundException(UUID positionId)
    {
        super("Paper position not found: " + positionId);
    }
}
