package com.mafiagame.config;

import com.mafiagame.dto.ValidationErrorResponse;
import com.mafiagame.exception.ApiException;
import com.mafiagame.exception.GameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        detail.setProperty("code", ex.code());
        return detail;
    }

    @ExceptionHandler(GameNotFoundException.class)
    public ProblemDetail handleGameNotFound(GameNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setProperty("code", "RESOURCE_NOT_FOUND");
        return detail;
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ValidationErrorResponse response = new ValidationErrorResponse(
                "Validation error",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }
}
