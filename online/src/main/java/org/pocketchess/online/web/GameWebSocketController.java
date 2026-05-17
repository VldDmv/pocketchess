package org.pocketchess.online.web;

import org.pocketchess.online.game.GameService;
import org.pocketchess.online.game.Messages;
import org.pocketchess.online.security.CurrentUser;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class GameWebSocketController {

    private final GameService gameService;

    public GameWebSocketController(GameService gameService) {
        this.gameService = gameService;
    }

    @MessageMapping("/game/{id}/move")
    public void onMove(@DestinationVariable("id") String gameId,
                       @Payload Messages.MoveRequest req,
                       Principal principal) {
        gameService.applyMove(gameId, CurrentUser.displayNameOf(principal), req.uci());
    }

    @MessageMapping("/game/{id}/resign")
    public void onResign(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.resign(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/draw/offer")
    public void onDrawOffer(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.offerDraw(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/draw/decline")
    public void onDrawDecline(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.declineDraw(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/undo/request")
    public void onUndoRequest(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.requestUndo(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/undo/accept")
    public void onUndoAccept(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.acceptUndo(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/undo/decline")
    public void onUndoDecline(@DestinationVariable("id") String gameId, Principal principal) {
        gameService.declineUndo(gameId, CurrentUser.displayNameOf(principal));
    }

    @MessageMapping("/game/{id}/chat")
    public void onChat(@DestinationVariable("id") String gameId,
                       @Payload Messages.ChatRequest req,
                       Principal principal) {
        gameService.postChat(gameId, CurrentUser.displayNameOf(principal), req.text());
    }
}
