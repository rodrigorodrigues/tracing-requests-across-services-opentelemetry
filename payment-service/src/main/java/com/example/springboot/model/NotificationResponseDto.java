package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record NotificationResponseDto(@NotBlank String usernameFrom, @NotBlank String usernameTo, @NotBlank String requestId, @NotNull BigDecimal total, @NotNull Long remainingTimeInSeconds) {
}
