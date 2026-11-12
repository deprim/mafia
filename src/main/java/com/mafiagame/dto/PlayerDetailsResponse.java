package com.mafiagame.dto;

import com.mafiagame.model.Player;
import com.mafiagame.utils.enums.PlayerRole;
import com.mafiagame.utils.enums.PlayerStatus;

import java.util.List;
import java.util.UUID;

public record PlayerDetailsResponse(
        UUID id,
        String nickname,
        boolean host,
        PlayerStatus status,
        PlayerRole role,
        List<PlayerResponse> mafiaTeammates
) {
    public static PlayerDetailsResponse from(Player player) {
        return from(player, List.of());
    }

    public static PlayerDetailsResponse from(Player player, List<PlayerResponse> mafiaTeammates) {
        return new PlayerDetailsResponse(
                player.getId(),
                player.getNickname(),
                player.isHost(),
                player.getStatus(),
                player.getRole(),
                mafiaTeammates
        );
    }
}
