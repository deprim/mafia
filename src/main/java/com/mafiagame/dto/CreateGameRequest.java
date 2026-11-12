package com.mafiagame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(
        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 40,
                message = "Nickname must be between 2 and 40 characters")
        String nickname
) {
    public CreateGameRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
