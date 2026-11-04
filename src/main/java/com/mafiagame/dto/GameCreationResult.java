package com.mafiagame.dto;

/**
 * Internal DTO only for communication between service and controller,
 * Controller responsible to write rawToken in player header, it shouldn't be return as ResponseBody
 * @param game - should be returned as ResponseBody
 * @param rawToken - should be returned as Header
 */
public record GameCreationResult(
        GameResponse game,
        String rawToken
) {
}
