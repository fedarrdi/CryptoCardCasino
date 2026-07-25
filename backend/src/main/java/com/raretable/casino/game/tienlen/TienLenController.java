package com.raretable.casino.game.tienlen;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.security.WalletPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tables/{tableId}/tien-len")
public final class TienLenController
{
    private final TienLenGameService tienLenGameService;

    public TienLenController(TienLenGameService tienLenGameService)
    {
        this.tienLenGameService = tienLenGameService;
    }

    @GetMapping("/state")
    public TienLenGameState getGameState(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tienLenGameService.getGameState(tableId, principal.userId());
    }

    @PostMapping("/actions/play")
    public TienLenGameState playCards(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal,
        @Valid @RequestBody PlayTienLenCardsRequest request
    )
    {
        return tienLenGameService.playCards(
            tableId,
            principal.userId(),
            request.cardIndexes(),
            request.combinationType()
        );
    }

    @PostMapping("/actions/pass")
    public TienLenGameState pass(
        @PathVariable UUID tableId,
        @AuthenticationPrincipal WalletPrincipal principal
    )
    {
        return tienLenGameService.pass(tableId, principal.userId());
    }
}
