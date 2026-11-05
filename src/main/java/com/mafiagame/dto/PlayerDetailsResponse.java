package com.mafiagame.dto;

import com.mafiagame.model.Player;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;

import java.util.UUID;

public record PlayerDetailsResponse(
        UUID id,
        String nickname,
        boolean host,
        PlayerStatus status,
        PlayerRole role
) {
    public static PlayerDetailsResponse from(Player player) {
        return new PlayerDetailsResponse(
                player.getId(),
                player.getNickname(),
                player.isHost(),
                player.getStatus(),
                player.getRole()
        );
    }
}
