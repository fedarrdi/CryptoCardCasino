package com.raretable.casino.paper_trading.trading;

import java.util.UUID;

public final class PaperPositionNotOpenException extends RuntimeException
{
    public PaperPositionNotOpenException(UUID positionId)
    {
        super("Paper position is already closed: " + positionId);
    }
}
