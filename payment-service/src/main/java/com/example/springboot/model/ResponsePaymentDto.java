package com.example.springboot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.Instant;

public record ResponsePaymentDto(String requestId,
                                 BigDecimal total,
                                 PaymentStatus status,
                                 String usernameFrom,
                                 String usernameTo,
                                 @JsonIgnore String usernameFromAddress,
                                 @JsonIgnore String usernameToAddress,
                                 Instant createdAt,
                                 Instant processedAt,
                                 String message,
                                 BigDecimal updatedBalanceUsernameFrom) {
    public ResponsePaymentDto(Payment payment, UserAuth usernameFrom, UserAuth usernameTo) {
        this(payment.getRequestId(),
                payment.getTotal(),
                payment.getStatus(),
                usernameFrom.getUsername(),
                usernameTo.getUsername(),
                usernameFrom.getAddress(),
                usernameTo.getAddress(),
                payment.getCreatedAt(),
                payment.getProcessedAt(),
                payment.getMessage(),
                null);
    }
    public ResponsePaymentDto(Payment payment) {
        this(payment.getRequestId(),
                payment.getTotal(),
                payment.getStatus(),
                payment.getUsernameFrom(),
                payment.getUsernameTo(),
                null,
                null,
                payment.getCreatedAt(),
                payment.getProcessedAt(),
                payment.getMessage(),
                null);
    }
    public ResponsePaymentDto(Payment payment, BigDecimal updatedBalance) {
        this(payment.getRequestId(),
                payment.getTotal(),
                payment.getStatus(),
                payment.getUsernameFrom(),
                payment.getUsernameTo(),
                null,
                null,
                payment.getCreatedAt(),
                payment.getProcessedAt(),
                payment.getMessage(),
                updatedBalance);
    }
}
