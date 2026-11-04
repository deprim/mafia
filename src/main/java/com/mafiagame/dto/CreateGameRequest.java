

package com.mafiagame.dto;


public record CreateGameRequest(
        String nickname, // Need to know who the host is
        Integer mafiaCount
) {
}
