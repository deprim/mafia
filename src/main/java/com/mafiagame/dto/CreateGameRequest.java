
package com.mafiagame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateGameRequest(

        @NotNull(message = "Mafia count is required")
        @Min(value = 1, message = "Mafia count must be at least 1")
        Integer mafiaCount,

        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 40,
                message = "Nickname must be between 2 and 40 characters")
        String nickname

) {
}
