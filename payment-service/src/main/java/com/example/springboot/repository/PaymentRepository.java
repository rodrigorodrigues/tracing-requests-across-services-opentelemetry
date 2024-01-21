package com.example.springboot.repository;

import com.example.springboot.model.Payment;
import com.example.springboot.model.PaymentStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, Long>, CustomizedPaymentRepository {
    @Query("SELECT * FROM payment WHERE request_id = :requestId")
    Mono<Payment> findByRequestId(@Param("requestId") String requestId);

    @Query("SELECT * FROM payment WHERE username_from = :username OR status = 'COMPLETED' and username_to = :username ORDER BY created_at DESC")
    Flux<Payment> findByUsernameFrom(String username);

    @Override
    @Query("SELECT * FROM payment ORDER BY created_at DESC")
    Flux<Payment> findAll();

    @Modifying
    @Query("update payment SET status = :status, message = :reasonFailed, processed_at = :processedAt WHERE request_id = :requestId")
    Mono<Void> declinePayment(@Param("status") PaymentStatus status, @Param("reasonFailed") String reasonFailed, @Param("processedAt") Instant processedAt, @Param("requestId") String requestId);
}
