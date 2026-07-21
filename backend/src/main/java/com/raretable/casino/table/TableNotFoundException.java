package com.raretable.casino.table;

import java.util.UUID;

public final class TableNotFoundException extends RuntimeException
{
    public TableNotFoundException(UUID tableId)
    {
        super("Table not found: " + tableId);
    }
}
