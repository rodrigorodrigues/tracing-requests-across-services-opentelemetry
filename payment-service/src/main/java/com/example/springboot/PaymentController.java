package com.example.springboot;

import com.example.schema.avro.Payment;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${TOPIC_NAME:payment-topic}")
    private String topic;

    PaymentController(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PaymentDto>> getAllByUsernameFrom(@RequestParam(name = "usernameFrom") String usernameFrom) {
        log.info("Return list of payments");
        return ResponseEntity.ok(paymentService.getAllByUsernameFrom(usernameFrom));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDto> getById(@PathVariable String paymentId) {
        log.info("Return payment by id: " + paymentId);
        return paymentService.findById(paymentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found id: " + paymentId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentDto> create(@RequestBody @Valid PaymentDto paymentDto) {
        paymentDto = paymentService.save(paymentDto);
        sendKafkaMessage(paymentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentDto);
    }

    @PostMapping("/declinePayment/{paymentId}")
    public ResponseEntity<?> decline(@PathVariable String paymentId) {
        paymentService.declinePayment(paymentId);
        return ResponseEntity.noContent().build();
    }

    void sendKafkaMessage(PaymentDto paymentDto) {
        try {
            Payment payment = new Payment();
            payment.setRequestId(paymentDto.requestId());
            payment.setCreatedAt(Instant.now());
            payment.setTotal(paymentDto.total().setScale(2, RoundingMode.HALF_UP));
            payment.setUsernameFrom(paymentDto.usernameFrom());
            payment.setUsernameFromCountry(paymentDto.localeUsernameFrom().getCountry());
            payment.setUsernameTo(paymentDto.usernameTo());
            payment.setUsernameToCountry(paymentDto.localeUsernameTo().getCountry());
            payment.setStatus(paymentDto.status().name());
            log.info("Sending kafka message: {}", paymentDto);
            kafkaTemplate.send(topic, payment);
        } catch (Exception e) {
            log.error("Failed to send kafka message", e);
        }
    }
}
