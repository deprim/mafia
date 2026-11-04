package com.mafiagame.dto;

import java.util.UUID;

/**
 * Internal DTO only for communication between service and controller,
 * Controller responsible to write rawToken in player header, it shouldn't be return as ResponseBody
 * @param gameId - should be returned as ResponseBody
 * @param player - should be returned as ResponseBody
 * @param rawToken - should be returned as Header
 */
public record PlayerJoinResult(
        UUID gameId,
        PlayerResponse player,
        String rawToken
) {
}
