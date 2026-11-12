package com.mafiagame.exception;

import org.springframework.http.HttpStatus;

public final class GameAccessDeniedException extends ApiException {
    public GameAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "GAME_ACCESS_DENIED", message);
    }
}
