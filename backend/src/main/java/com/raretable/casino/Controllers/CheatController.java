package com.raretable.casino.Controllers;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.Games.CheatGameState;
import com.raretable.casino.Requests.PlayCardsRequest;
import com.raretable.casino.Services.CheatGameService;

@RestController
@RequestMapping("/api/tables/{tableId}/cheat")
public class CheatController
{
    private final CheatGameService cheatGameService;

    public CheatController(CheatGameService cheatGameService)
    {
        this.cheatGameService = cheatGameService;
    }

    @GetMapping("/users/{userId}/state")
    public CheatGameState get_game_state(@PathVariable UUID tableId, @PathVariable UUID userId)
    {
        return cheatGameService.get_game_state(tableId, userId);
    }

    @PostMapping("/users/{userId}/play")
    public CheatGameState play_cards(
        @PathVariable UUID tableId,
        @PathVariable UUID userId,
        @RequestBody PlayCardsRequest request
    )
    {
        return cheatGameService.play_cards(
            tableId,
            userId,
            request.getCardIndexes(),
            request.getDeclaredRank()
        );
    }

    @PostMapping("/users/{userId}/call-bluff")
    public CheatGameState call_bluff(@PathVariable UUID tableId, @PathVariable UUID userId)
    {
        return cheatGameService.call_bluff(tableId, userId);
    }
}
