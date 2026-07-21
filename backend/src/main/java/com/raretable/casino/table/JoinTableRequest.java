package com.raretable.casino.table;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record JoinTableRequest(@NotNull UUID userId)
{
}
