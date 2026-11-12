package com.mafiagame.exception;

import org.springframework.http.HttpStatus;

public final class InvalidRequestException extends ApiException {
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }
}
