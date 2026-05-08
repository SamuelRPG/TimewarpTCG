package com.saroga.timewarp.infrastructure.web.dto.response;

public class ErrorResponseDto {
    public String type = "ERROR";
    public String message;

    public ErrorResponseDto(String message) {
        this.message = message;
    }
}
