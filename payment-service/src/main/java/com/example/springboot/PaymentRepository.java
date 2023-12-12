package com.example.springboot;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends CrudRepository<Payment, String> {
    @Query("SELECT new com.example.springboot.PaymentDto(p) FROM Payment p WHERE p.paymentId = :paymentId")
    Optional<PaymentDto> findPaymentByPaymentId(@Param("paymentId") String paymentId);

    @Modifying
    @Query("update Payment SET status = :status WHERE paymentId = :paymentId")
    void declinePayment(@Param("status") PaymentStatus status, @Param("paymentId") String paymentId);
}
