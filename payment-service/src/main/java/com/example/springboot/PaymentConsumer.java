package com.example.springboot;

import com.example.schema.avro.CheckStatus;
import com.example.schema.avro.UpdatePayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
public class PaymentConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentRepository paymentRepository;

    private final Executor singleThread = Executors.newSingleThreadExecutor();

    public PaymentConsumer(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @RetryableTopic(
            backoff = @Backoff(value = 6000),
            attempts = "4",
            autoCreateTopics = "false",
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            exclude = {NullPointerException.class}
    )
    @KafkaListener(topics = "update-payment-topic")
    @Transactional
    public void consume(UpdatePayment updatePayment, @Headers MessageHeaders headers) {
        log.info("### -> Receiving headers: {}", headers);
        byte[] traceId = headers.get("X-B3-TraceId", byte[].class);
        if (traceId != null) {
            log.info("### -> Processing traceID={}", new String(traceId, StandardCharsets.UTF_8));
        }
        Optional<Payment> optional = paymentRepository.findById(updatePayment.getPaymentId());
        if (optional.isEmpty()) {
            throw new IllegalStateException("Not found paymentId: "+updatePayment.getPaymentId());
        }

        log.info("### -> Processing updatePayment: {}", updatePayment);

        Payment payment = optional.get();
        CheckStatus status = updatePayment.getStatus();
        boolean checkFailed = updatePayment.getCheckFailed();
        if (checkFailed) {
            payment.setStatus(PaymentStatus.DECLINED);
            payment.setReasonFailed(updatePayment.getReasonFailed());
        }
        if (status == CheckStatus.SANCTION_CHECK) {
            payment.setSanctionCheckProcessed(true);
        } else if (status == CheckStatus.AUTH_CHECK) {
            payment.setAuthCheckProcessed(true);
        } else if (status == CheckStatus.USER_CONFIRMATION_CHECK) {
            payment.setUserConfirmationCheckProcessed(true);
        }

        if (payment.isAllChecksProcessed()) {
            singleThread.execute(() -> payment.processPayment(payment));
        }
        paymentRepository.save(payment);
        Acknowledgment ack = headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
        if (Objects.nonNull(ack)) ack.acknowledge();
    }

    @DltHandler
    public void dlt(UpdatePayment data, @Headers MessageHeaders headers) {
        log.error("Event from topic {}  is dead lettered - event:{}", headers, data);
    }
}
