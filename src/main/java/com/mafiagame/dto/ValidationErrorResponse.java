package com.mafiagame.dto;

import java.util.Map;

public record ValidationErrorResponse(String message, Map<String, String> errors) {
}
