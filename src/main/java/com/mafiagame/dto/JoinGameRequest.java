
package com.mafiagame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record JoinGameRequest(

        @NotBlank(message = "Join code is required")
        @Size(min = 8, max = 8, message = "Join code must contain 8 characters")
        String joinCode,

        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 40, message = "Nickname must be between 2 and 40 characters")
        String nickname

) {
    public JoinGameRequest {
        joinCode = joinCode == null ? null : joinCode.trim().toUpperCase(Locale.ROOT);
        nickname = nickname == null ? null : nickname.trim();
    }
}
