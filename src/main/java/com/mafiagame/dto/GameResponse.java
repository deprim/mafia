
package com.mafiagame.dto;

import com.mafiagame.model.Game;
import com.mafiagame.utils.enums.GameStatus;

import java.util.UUID;

public record GameResponse(
        UUID id,
        String joinCode,
        Integer mafiaCount,
        GameStatus status
) {

    public static GameResponse from(Game game) {

        return new GameResponse(game.getId(),
                game.getJoinCode(),
                game.getMafiaCount(),
                game.getStatus());

    }

}
