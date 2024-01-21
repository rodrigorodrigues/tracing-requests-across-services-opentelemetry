package com.example.springboot.repository;

import com.example.springboot.model.Payment;
import reactor.core.publisher.Flux;

public interface CustomizedPaymentRepository {
    Flux<Payment> findExpiredPayments(long seconds);
}
