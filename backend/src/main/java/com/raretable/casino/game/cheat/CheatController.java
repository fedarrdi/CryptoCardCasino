package com.raretable.casino.game.cheat;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tables/{tableId}/cheat/users/{userId}")
public final class CheatController
{
    private final CheatGameService cheatGameService;

    public CheatController(CheatGameService cheatGameService)
    {
        this.cheatGameService = cheatGameService;
    }

    @GetMapping("/state")
    public CheatGameState getGameState(
        @PathVariable UUID tableId,
        @PathVariable UUID userId
    )
    {
        return cheatGameService.getGameState(tableId, userId);
    }

    @PostMapping("/actions/play")
    public CheatGameState playCards(
        @PathVariable UUID tableId,
        @PathVariable UUID userId,
        @Valid @RequestBody PlayCardsRequest request
    )
    {
        return cheatGameService.playCards(
            tableId,
            userId,
            request.cardIndexes(),
            request.declaredRank()
        );
    }

    @PostMapping("/actions/call-bluff")
    public CheatGameState callBluff(
        @PathVariable UUID tableId,
        @PathVariable UUID userId
    )
    {
        return cheatGameService.callBluff(tableId, userId);
    }
}
