package com.example.springboot.service;

import com.example.schema.avro.CheckStatus;
import com.example.schema.avro.UpdatePayment;
import com.example.springboot.config.PaymentProperties;
import com.example.springboot.model.*;
import com.example.springboot.repository.PaymentRepository;
import com.example.springboot.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.reactive.TransactionalEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;

    private final UserRepository userRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Value("${TOPIC_NAME:payment-topic}")
    private String topic;
    private static final Map<String, List<ResponsePaymentDto>> mapPayments = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private final PaymentProperties paymentProperties;
    private final ReactiveRedisOperations<String, NotificationResponseDto> redisOperations;
    private final ObjectMapper objectMapper;

    PaymentService(PaymentRepository paymentRepository, UserRepository userRepository, ApplicationEventPublisher applicationEventPublisher,
                   KafkaTemplate<String, Object> kafkaTemplate, PaymentProperties paymentProperties,
                   ReactiveRedisOperations<String, NotificationResponseDto> redisOperations, ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentProperties = paymentProperties;
        this.redisOperations = redisOperations;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Flux<ResponsePaymentDto> declineExpiredPayments() {
        return paymentRepository.findExpiredPayments(paymentProperties.getExpirePaymentInSecs())
                .flatMap(p -> {
                    log.debug("Declining payment: {}", p);
                    p.setMessage("\uD83D\uDE21 Payment expired!");
                    p.setStatus(PaymentStatus.DECLINED);
                    p.setProcessedAt(Instant.now());
                    return paymentRepository.save(p);
                }).flatMap(p -> publishPaymentEvent(new ResponsePaymentDto(p)));
    }


    public Flux<NotificationResponseDto> getNotificationsByUsername(String username) {
        return redisOperations.opsForHash().values("notificationKey")
                .<NotificationResponseDto>handle((p, sink) -> {
                    try {
                        sink.next(objectMapper.readValue(p.toString(), NotificationResponseDto.class));
                    } catch (JsonProcessingException e) {
                        sink.error(new RuntimeException("Could not convert json", e));
                    }
                })
                .filter(n -> n.username().equals(username));
    }

    @TransactionalEventListener
    public void listenLastPaymentChanges(PaymentEvent paymentEvent) {
        log.info("Listening events after committing transaction: {}", paymentEvent);
        ResponsePaymentDto payment = paymentEvent.payment();
        if (payment.status() == PaymentStatus.COMPLETED) {
            List<ResponsePaymentDto> lastPaymentsModified = mapPayments.computeIfAbsent(payment.usernameTo(), k -> new ArrayList<>());
            lastPaymentsModified.add(payment);
        }
        List<ResponsePaymentDto> lastPaymentsModified = mapPayments.computeIfAbsent(payment.usernameFrom(), k -> new ArrayList<>());
        lastPaymentsModified.add(payment);
    }

    private Flux<ResponsePaymentDto> getLastPaymentsModifiedByUsername(String username) {
        log.debug("Getting getLastPaymentsModifiedByUsername by username: {}", username);
        List<ResponsePaymentDto> payments = mapPayments.remove(username);
        return payments != null ? Flux.fromIterable(payments) : Flux.empty();
    }

    @Transactional(readOnly = true)
    @PostFilter("hasAuthority('ADMIN') or filterObject.usernameFrom == @paymentService.getUsername(authentication) or filterObject.usernameTo == @paymentService.getUsername(authentication)")
    public Flux<ResponsePaymentDto> getAllByUsernameFrom(String username, AtomicBoolean firstVisit) {
        log.debug("Calling getAllByUsernameFrom by username: {}={}", username, firstVisit.get());
        Flux<ResponsePaymentDto> payments = Flux.empty();
        if (firstVisit.getAndSet(false)) {
            if (username.equals("admin")) {
                payments = paymentRepository.findAll()
                        .map(ResponsePaymentDto::new);
            } else {
                payments = paymentRepository.findByUsernameFrom(username)
                        .map(ResponsePaymentDto::new);
            }
        }
        return payments.mergeWith(getLastPaymentsModifiedByUsername(username));
    }

    @Transactional(readOnly = true)
    @PostFilter("hasAuthority('ADMIN') or filterObject.usernameFrom == @paymentService.getUsername(authentication)")
    public Mono<ResponsePaymentDto> findById(String requestId) {
        return paymentRepository.findByRequestId(requestId)
                .map(ResponsePaymentDto::new);
    }

    @Transactional
    public Mono<ResponsePaymentDto> processNotification(NotificationRequestDto notificationRequestDto) {
        Instant now = Instant.now();
        String requestId = notificationRequestDto.requestId();
        return paymentRepository.findByRequestId(requestId)
                .flatMap(p -> {
                    if (isPaymentExpired(p.getCreatedAt(), now)) {
                        log.info("Payment expired: {}", p);
                        p.setStatus(PaymentStatus.DECLINED);
                        p.setMessage("\uD83D\uDE21 Payment expired!");
                        return paymentRepository.save(p)
                                .map(ResponsePaymentDto::new);
                    } else if (!notificationRequestDto.confirmed()) {
                        log.info("User has declined payment: {}", p);
                        p.setStatus(PaymentStatus.DECLINED);
                        p.setMessage("\uD83D\uDE21 User has declined payment!");
                        return paymentRepository.save(p)
                                .map(ResponsePaymentDto::new);
                    }
                    return completePayment(p)
                            .doOnNext(resp -> log.info("Processed payment: {}", resp));
                })
                .flatMap(p -> redisOperations.opsForHash().remove("notificationKey", notificationRequestDto.requestId())
                        .then(publishPaymentEvent(p)));
    }

    public String getUsername(Authentication authentication) {
        String username = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2) {
            username = oauth2.getPrincipal().getAttribute("email");
        }
        return username;
    }

    @Transactional
    public Mono<ResponsePaymentDto> save(final RequestPaymentDto requestPaymentDto, String username) {
        if (requestPaymentDto.usernameTo().equals(username)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot make a payment for yourself!"));
        }
        return convertToMonoZip(username, requestPaymentDto.usernameTo())
                .flatMap(u -> paymentRepository.findByRequestId(requestPaymentDto.requestId())
                        .flatMap(p -> {
                            if (p.getStatus() == PaymentStatus.COMPLETED) {
                                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reprocess a COMPLETED payment!"));
                            } else {
                                return paymentRepository.save(new Payment(p.getId(), requestPaymentDto, username))
                                        .map(pp -> new ResponsePaymentDto(pp, u.getT1(), u.getT2()))
                                        .flatMap(processKafkaMessage());
                            }
                        })
                        .switchIfEmpty(paymentRepository.save(new Payment(null, requestPaymentDto, username))
                                .map(pp -> new ResponsePaymentDto(pp, u.getT1(), u.getT2()))
                                .flatMap(processKafkaMessage()))
                );
    }

    private Function<ResponsePaymentDto, Mono<? extends ResponsePaymentDto>> processKafkaMessage() {
        return resp -> Mono.defer(() -> {
            executor.execute(() -> sendKafkaMessage(resp));
            log.info("paymentDto: {}", resp);
            return publishPaymentEvent(resp);
        });
    }

    private Mono<ResponsePaymentDto> publishNotificationEvent(ResponsePaymentDto resp) {
        NotificationResponseDto notification = new NotificationResponseDto(resp.usernameTo(),
                resp.requestId(),
                resp.total(),
                Duration.between(resp.createdAt(), Instant.now()).getSeconds());
        try {
            return redisOperations.opsForHash().put("notificationKey", notification.requestId(), objectMapper.writeValueAsString(notification))
                    .thenReturn(resp);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not convert to json", e);
        }
    }

    private Mono<ResponsePaymentDto> publishPaymentEvent(ResponsePaymentDto resp) {
        log.debug("Publishing payment: {}", resp);
        return new TransactionalEventPublisher(applicationEventPublisher).publishEvent(new PaymentEvent(resp))
                .thenReturn(resp);
    }

    private void sendKafkaMessage(ResponsePaymentDto responsePaymentDto) {
        try {
            com.example.schema.avro.Payment payment = new com.example.schema.avro.Payment();
            payment.setRequestId(responsePaymentDto.requestId());
            payment.setCreatedAt(Instant.now());
            payment.setTotal(responsePaymentDto.total().setScale(2, RoundingMode.HALF_UP));
            payment.setUsernameFrom(responsePaymentDto.usernameFrom());
            payment.setUsernameFromAddress(responsePaymentDto.usernameFromAddress());
            payment.setUsernameTo(responsePaymentDto.usernameTo());
            payment.setUsernameToAddress(responsePaymentDto.usernameToAddress());
            payment.setStatus(responsePaymentDto.status().name());
            log.info("Sending kafka message: {}", responsePaymentDto);
            kafkaTemplate.send(topic, payment);
        } catch (Exception e) {
            log.error("Failed to send kafka message", e);
        }
    }

    private Mono<Tuple2<UserAuth, UserAuth>> convertToMonoZip(String usernameFrom, String usernameTo) {
        return Mono.zip(findByUser(usernameFrom), findByUser(usernameTo));
    }

    private Mono<UserAuth> findByUser(String username) {
        return userRepository.findById(username)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Not found username: " + username)));
    }

    @Transactional
    public Mono<Void> declinePayment(String requestId, Instant processedAt) {
        return paymentRepository.declinePayment(PaymentStatus.DECLINED, "\uD83D\uDE31 User declined", processedAt, requestId);
    }

    @Transactional
    public Mono<ResponsePaymentDto> processPayment(UpdatePayment updatePayment) {
        return paymentRepository.findByRequestId(updatePayment.getRequestId())
                .switchIfEmpty(Mono.error(new IllegalStateException("Not found Payment for requestId: "+ updatePayment.getRequestId())))
                .flatMap(payment -> {
                    log.info("### -> Processing payment: {}", payment);
                    log.info("### -> Processing updatePayment: {}", updatePayment);

                    CheckStatus status = updatePayment.getStatus();
                    boolean checkFailed = updatePayment.getCheckFailed();
                    payment.setProcessedAt(Instant.now());
                    if (checkFailed) {
                        payment.setStatus(PaymentStatus.DECLINED);
                        payment.setMessage(createReasonFailed(payment, updatePayment.getReasonFailed(), "\uD83D\uDC40"));
                    }
                    if (status == CheckStatus.SANCTION_CHECK) {
                        payment.setSanctionCheckProcessed(true);
                    } else if (status == CheckStatus.AUTH_CHECK) {
                        payment.setAuthCheckProcessed(true);
                    } else if (status == CheckStatus.USER_CONFIRMATION_CHECK) {
                        payment.setUserConfirmationCheckProcessed(true);
                    }

                    if (isPaymentExpired(payment.getCreatedAt(), updatePayment.getUpdateAt())) {
                        payment.setMessage("\uD83D\uDE21 Payment expired!");
                        payment.setStatus(PaymentStatus.DECLINED);
                    } else if (payment.isAllChecksProcessed() && !checkFailed) {
                        payment.setStatus(PaymentStatus.WAITING_FOR_USER_CONFIRMATION);
                    }

                    return paymentRepository.save(payment);
                })
                .flatMap(payment -> {
                    if (payment.isAllChecksProcessed() && payment.getStatus() != PaymentStatus.DECLINED) {
                        return publishNotificationEvent(new ResponsePaymentDto(payment))
                                .flatMap(this::publishPaymentEvent);
                    }

                    return publishPaymentEvent(new ResponsePaymentDto(payment));
                });

    }

    private boolean isPaymentExpired(Instant startTime, Instant endTime) {
        return Duration.between(startTime, endTime).getSeconds() > paymentProperties.getExpirePaymentInSecs();
    }

    private static String createReasonFailed(Payment payment, String msg, String emoji) {
        String message = (payment.getMessage() != null ? payment.getMessage() + ";" : "") + msg;
        if (message.codePoints().noneMatch(Character::isEmoji)) {
            return emoji + " " + message;
        }
        return message;
    }

    private Mono<ResponsePaymentDto> completePayment(Payment payment) {
        if (!payment.isAllChecksProcessed() || payment.getStatus() != PaymentStatus.WAITING_FOR_USER_CONFIRMATION) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has invalid details"));
        }
        return convertToMonoZip(payment.getUsernameFrom(), payment.getUsernameTo())
                .flatMap(t -> {
                    UserAuth usernameFrom = t.getT1();
                    UserAuth usernameTo = t.getT2();
                    BigDecimal total = payment.getTotal();
                    if (total.doubleValue() > usernameFrom.getBalance().doubleValue()) {
                        String reason = createReasonFailed(payment, "User has not sufficient resources", "\uD83D\uDE21");
                        payment.setStatus(PaymentStatus.INSUFFICIENT_RESOURCES);
                        payment.setMessage(reason);
                        payment.setProcessedAt(Instant.now());
                        return paymentRepository.save(payment);
                    } else {
                        usernameFrom.updateBalance(usernameTo, total);
                        return userRepository.saveAll(Arrays.asList(usernameFrom, usernameTo))
                                .flatMap(p -> {
                                    payment.setStatus(PaymentStatus.COMPLETED);
                                    payment.setMessage("\uD83E\uDD11 Payment successful");
                                    payment.setProcessedAt(Instant.now());
                                    return paymentRepository.save(payment);
                                }).last();
                    }
                })
                .flatMap(p -> publishPaymentEvent(new ResponsePaymentDto(p)));
    }
}
