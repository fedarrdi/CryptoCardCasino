package com.raretable.casino.game.cheat;

import java.util.List;

import com.raretable.casino.common.Rank;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PlayCardsRequest(
    @NotEmpty List<@NotNull @PositiveOrZero Integer> cardIndexes,
    @NotNull Rank declaredRank
)
{
}
