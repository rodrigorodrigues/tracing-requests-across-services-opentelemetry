package com.example.springboot.service;

import com.example.schema.avro.UpdatePayment;
import com.example.springboot.repository.PaymentRepository;
import com.example.springboot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.retry.annotation.Backoff;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
public class PaymentConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);

    private final PaymentRepository paymentRepository;

    private final PaymentService paymentService;

    private final UserRepository userRepository;

    private final Executor singleThread = Executors.newSingleThreadExecutor();

    public PaymentConsumer(PaymentRepository paymentRepository, PaymentService paymentService, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.userRepository = userRepository;
    }

    @RetryableTopic(
            backoff = @Backoff(value = 6000),
            attempts = "3",
            autoCreateTopics = "false",
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            exclude = {NullPointerException.class}
    )
    @KafkaListener(topics = "${UPDATE_TOPIC_NAME:update-payment-topic}")
    public void consume(UpdatePayment updatePayment, @Headers MessageHeaders headers) {
        log.info("### -> Receiving headers: {}", headers);
        extractHeaders(headers, "X-B3-SpanId", "spanId", null);
        extractHeaders(headers, "requestId", "requestId", null);
        extractHeaders(headers, "X-B3-TraceId", "traceId", "### -> Processing traceID={}");
        paymentService.processPayment(updatePayment)
                .subscribe(p -> {
                    log.info("### -> Processed Payment: {}", p);
                    Acknowledgment ack = headers.get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
                    if (Objects.nonNull(ack)) ack.acknowledge();
                });
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 1000)
    public void scheduledExpiredPayment() {
        paymentService.declineExpiredPayments()
                .subscribe(p -> log.debug("Declined payment - reason expired time: {}", p));
    }

    private void extractHeaders(MessageHeaders headers, String header, String mdcKey, String msg) {
        byte[] bytes = headers.get(header, byte[].class);
        if (bytes != null) {
            String value = new String(bytes, StandardCharsets.UTF_8);
            MDC.put(mdcKey, value);
            if (msg != null) {
                log.info(msg, value);
            }
        }
    }

    @DltHandler
    public void dlt(UpdatePayment data, @Headers MessageHeaders headers) {
        log.error("Event from topic {}  is dead lettered - event:{}", headers, data);
    }
}
