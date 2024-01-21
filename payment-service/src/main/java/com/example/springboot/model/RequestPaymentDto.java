package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record RequestPaymentDto(@NotBlank String requestId,
                                @NotNull BigDecimal total,
                                @NotBlank String usernameTo,
                                Instant createdAt) {
}
