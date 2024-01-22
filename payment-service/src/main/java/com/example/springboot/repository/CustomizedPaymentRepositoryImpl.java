package com.example.springboot.repository;

import com.example.springboot.model.Payment;
import com.example.springboot.model.PaymentStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Query;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

public class CustomizedPaymentRepositoryImpl implements CustomizedPaymentRepository {
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    public CustomizedPaymentRepositoryImpl(R2dbcEntityTemplate r2dbcEntityTemplate) {
        this.r2dbcEntityTemplate = r2dbcEntityTemplate;
    }

    @Override
    public Flux<Payment> findExpiredPayments(long seconds) {
        Instant now = Instant.now();
        Query query = query(where("status").in(getStatus()));
        return r2dbcEntityTemplate.select(query, Payment.class)
                .filter(p -> p.getCreatedAt().plusSeconds(seconds).isBefore(now));
    }

    private List<String> getStatus() {
        return Arrays.asList(PaymentStatus.PROCESSING.name(), PaymentStatus.REPROCESSING.name(), PaymentStatus.WAITING_FOR_USER_CONFIRMATION.name());
    }
}
