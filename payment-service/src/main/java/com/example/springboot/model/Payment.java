package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Table("payment")
public class Payment {
    @Id
    private Long id;

    @NotBlank
    private String requestId;

    @NotNull
    private BigDecimal total;

    @NotBlank
    private String usernameFrom;

    @NotNull
    @NotBlank
    private String usernameTo;

    @NotNull
    private PaymentStatus status;

    @NotNull
    private Instant createdAt;

    private Instant processedAt;

    private boolean sanctionCheckProcessed;

    private boolean authCheckProcessed;

    private boolean userConfirmationCheckProcessed;

    private String message;

    public Payment() {}

    public Payment(Long id, RequestPaymentDto requestPaymentDto, String usernameFrom) {
        this.id = id;
        this.requestId = requestPaymentDto.requestId();
        this.total = requestPaymentDto.total();
        this.usernameFrom = usernameFrom;
        this.usernameTo = requestPaymentDto.usernameTo();
        this.status = (id != null ? PaymentStatus.REPROCESSING : PaymentStatus.PROCESSING);
        this.createdAt = Instant.now();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getUsernameFrom() {
        return usernameFrom;
    }

    public void setUsernameFrom(String usernameFrom) {
        this.usernameFrom = usernameFrom;
    }

    public String getUsernameTo() {
        return usernameTo;
    }

    public void setUsernameTo(String usernameTo) {
        this.usernameTo = usernameTo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return sanctionCheckProcessed == payment.sanctionCheckProcessed &&
                authCheckProcessed == payment.authCheckProcessed &&
                userConfirmationCheckProcessed == payment.userConfirmationCheckProcessed &&
                Objects.equals(id, payment.id) &&
                Objects.equals(requestId, payment.requestId) &&
                Objects.equals(total, payment.total) &&
                Objects.equals(usernameFrom, payment.usernameFrom) &&
                Objects.equals(usernameTo, payment.usernameTo) &&
                status == payment.status &&
                Objects.equals(createdAt, payment.createdAt) &&
                Objects.equals(processedAt, payment.processedAt) &&
                Objects.equals(message, payment.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, requestId, total, usernameFrom, usernameTo, status, createdAt, processedAt, sanctionCheckProcessed, authCheckProcessed, userConfirmationCheckProcessed, message);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "requestId='" + requestId + '\'' +
                ", id=" + id +
                ", total=" + total +
                ", usernameFrom=" + usernameFrom +
                ", usernameTo=" + usernameTo +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", processedAt=" + processedAt +
                ", sanctionCheckProcessed=" + sanctionCheckProcessed +
                ", authCheckProcessed=" + authCheckProcessed +
                ", userConfirmationCheckProcessed=" + userConfirmationCheckProcessed +
                ", message='" + message + '\'' +
                '}';
    }
}
