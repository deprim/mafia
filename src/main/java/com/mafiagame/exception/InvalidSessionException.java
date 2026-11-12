package com.mafiagame.exception;

import org.springframework.http.HttpStatus;

public final class InvalidSessionException extends ApiException {
    public InvalidSessionException(String message) {
        super(HttpStatus.UNAUTHORIZED, "INVALID_SESSION", message);
    }
}
