package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.math.BigDecimal;

@RedisHash
public record NotificationResponseDto(@NotBlank String username, @NotBlank String requestId, @NotNull BigDecimal total, @TimeToLive @NotNull Long remainingTimeInSeconds) {
}
