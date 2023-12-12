package com.example.springboot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record PaymentDto(@NotBlank String requestId, @NotNull BigDecimal total, PaymentStatus status, @NotBlank String usernameFrom, @NotBlank String usernameTo,
                         @JsonIgnore Locale localeUsernameFrom, @JsonIgnore Locale localeUsernameTo,
                         Instant createdAt,
                         Instant processedAt) {
    public PaymentDto(Payment payment) {
        this(payment.getPaymentId(),
                payment.getTotal(),
                payment.getStatus(),
                payment.getUsernameFrom().getUsername(),
                payment.getUsernameTo().getUsername(),
                payment.getUsernameFrom().getLocale(),
                payment.getUsernameTo().getLocale(),
                payment.getCreatedAt(),
                payment.getProcessedAt());
    }
}
