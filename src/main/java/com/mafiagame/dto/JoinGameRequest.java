
package com.mafiagame.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinGameRequest(

        @NotBlank(message = "Join code is required")
        @Size(min = 8, max = 8,
        message = "Joincode must contain 8 symbols")
        String joinCode,

        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 40,
                message = "Nickname must be between 2 and 40 characters")
        String nickname

) {
}
