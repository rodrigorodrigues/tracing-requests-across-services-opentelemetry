package com.example.springboot.model;

public enum PaymentStatus {
    PROCESSING,
    REPROCESSING,
    WAITING_FOR_USER_CONFIRMATION,
    COMPLETED,
    INSUFFICIENT_RESOURCES,
    DECLINED
}
