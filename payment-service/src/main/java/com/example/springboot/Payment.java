package com.example.springboot;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "tb_payment")
public class Payment {
    @Id
    @NotBlank
    private String paymentId;

    @NotNull
    private BigDecimal total;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "usernameFrom")
    private User usernameFrom;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "usernameTo")
    private User usernameTo;

    @NotNull
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @NotNull
    private Instant createdAt;

    private Instant processedAt;

    private boolean sanctionCheckProcessed;

    private boolean authCheckProcessed;

    private boolean userConfirmationCheckProcessed;

    private String reasonFailed;

    public Payment() {
    }

    public Payment(PaymentDto paymentDto, User usernameFrom, User usernameTo) {
        this.paymentId = paymentDto.requestId();
        this.total = paymentDto.total();
        this.usernameFrom = usernameFrom;
        this.usernameTo = usernameTo;
        this.status = PaymentStatus.PROCESSING;
        this.createdAt = (paymentDto.createdAt() != null ? paymentDto.createdAt() : Instant.now());
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public boolean isSanctionCheckProcessed() {
        return sanctionCheckProcessed;
    }

    public void setSanctionCheckProcessed(boolean sanctionCheckProcessed) {
        this.sanctionCheckProcessed = sanctionCheckProcessed;
    }

    public boolean isAuthCheckProcessed() {
        return authCheckProcessed;
    }

    public void setAuthCheckProcessed(boolean authCheckProcessed) {
        this.authCheckProcessed = authCheckProcessed;
    }

    public boolean isUserConfirmationCheckProcessed() {
        return userConfirmationCheckProcessed;
    }

    public void setUserConfirmationCheckProcessed(boolean userConfirmationCheckProcessed) {
        this.userConfirmationCheckProcessed = userConfirmationCheckProcessed;
    }

    public boolean isAllChecksProcessed() {
        return isAuthCheckProcessed() && isSanctionCheckProcessed() && isUserConfirmationCheckProcessed();
    }

    public void processPayment(Payment payment) {
        User usernameFrom = payment.getUsernameFrom();
        User usernameTo = payment.getUsernameTo();
        BigDecimal total = payment.getTotal();
        if (total.doubleValue() > usernameFrom.getBalance().doubleValue()) {
            payment.setStatus(PaymentStatus.INSUFFICIENT_RESOURCES);
            payment.setReasonFailed("User not has insufficient resources");
        } else {
            usernameTo.setBalance(usernameTo.getBalance().add(total));
            usernameFrom.setBalance(usernameFrom.getBalance().subtract(total));
            payment.setStatus(PaymentStatus.COMPLETED);
        }
    }

    public String getReasonFailed() {
        return reasonFailed;
    }

    public void setReasonFailed(String reasonFailed) {
        this.reasonFailed = reasonFailed;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public User getUsernameFrom() {
        return usernameFrom;
    }

    public void setUsernameFrom(User usernameFrom) {
        this.usernameFrom = usernameFrom;
    }

    public User getUsernameTo() {
        return usernameTo;
    }

    public void setUsernameTo(User usernameTo) {
        this.usernameTo = usernameTo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return sanctionCheckProcessed == payment.sanctionCheckProcessed && authCheckProcessed == payment.authCheckProcessed && userConfirmationCheckProcessed == payment.userConfirmationCheckProcessed && Objects.equals(paymentId, payment.paymentId) && Objects.equals(total, payment.total) && Objects.equals(usernameFrom, payment.usernameFrom) && Objects.equals(usernameTo, payment.usernameTo) && status == payment.status && Objects.equals(createdAt, payment.createdAt) && Objects.equals(processedAt, payment.processedAt) && Objects.equals(reasonFailed, payment.reasonFailed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentId, total, usernameFrom, usernameTo, status, createdAt, processedAt, sanctionCheckProcessed, authCheckProcessed, userConfirmationCheckProcessed, reasonFailed);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId='" + paymentId + '\'' +
                ", total=" + total +
                ", usernameFrom=" + usernameFrom +
                ", usernameTo=" + usernameTo +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", processedAt=" + processedAt +
                ", sanctionCheckProcessed=" + sanctionCheckProcessed +
                ", authCheckProcessed=" + authCheckProcessed +
                ", userConfirmationCheckProcessed=" + userConfirmationCheckProcessed +
                ", reasonFailed='" + reasonFailed + '\'' +
                '}';
    }
}
