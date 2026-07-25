package com.raretable.casino.game.tienlen;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlayTienLenCardsRequest(
    @NotEmpty List<@NotNull @PositiveOrZero Integer> cardIndexes,
    @NotNull TienLenCombinationType combinationType
)
{
}
