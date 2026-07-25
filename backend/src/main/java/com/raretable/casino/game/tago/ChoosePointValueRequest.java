package com.raretable.casino.game.tago;

import jakarta.validation.constraints.NotNull;

public record ChoosePointValueRequest(@NotNull Double value)
{
}
