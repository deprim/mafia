package com.mafiagame.exception;

import org.springframework.http.HttpStatus;

public final class GameConflictException extends ApiException {
    public GameConflictException(String message) {
        super(HttpStatus.CONFLICT, "GAME_CONFLICT", message);
    }
}
