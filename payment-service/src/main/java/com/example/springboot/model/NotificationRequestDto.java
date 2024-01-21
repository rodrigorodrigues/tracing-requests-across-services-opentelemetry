package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequestDto(@NotBlank String username, @NotBlank String requestId, @NotNull Boolean confirmed) {
}
