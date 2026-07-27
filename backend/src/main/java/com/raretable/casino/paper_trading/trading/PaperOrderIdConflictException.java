package com.raretable.casino.paper_trading.trading;

import java.util.UUID;

public final class PaperOrderIdConflictException extends RuntimeException
{
    public PaperOrderIdConflictException(UUID clientOrderId)
    {
        super(
            "Client order id " + clientOrderId
                + " was already used for a different order"
        );
    }
}
