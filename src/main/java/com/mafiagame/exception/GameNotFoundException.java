package com.mafiagame.exception;

public final class GameNotFoundException extends IllegalArgumentException {
    public GameNotFoundException(String message) {
        super(message);
    }
}
