package com.example.springboot.controller;

import com.example.springboot.model.NotificationRequestDto;
import com.example.springboot.model.NotificationResponseDto;
import com.example.springboot.model.ResponsePaymentDto;
import com.example.springboot.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class DashboardController extends AbstractController {
    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final PaymentService paymentService;

    public DashboardController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping(value = "/api/v1/dashboard/payments", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ResponsePaymentDto>> getPayments(Authentication authentication) {
        String username = getUsername(authentication);
        log.info("Returning payments for: {}", username);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);
        httpHeaders.setCacheControl(CacheControl.noCache());
        httpHeaders.setConnection("keep-alive");
        httpHeaders.add("Content-Encoding", "none");
        Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));
        AtomicBoolean firstVisit = new AtomicBoolean(true);
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(interval.flatMap(p -> paymentService.getAllByUsernameFrom(username, firstVisit))
                        .doOnNext(p -> log.debug("Getting payment: {}", p)));
    }

    @GetMapping(value = "/api/v1/dashboard/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<NotificationResponseDto>> getNotifications(Authentication authentication) {
        String username = getUsername(authentication);
        log.info("Returning notifications for: {}", username);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);
        httpHeaders.setCacheControl(CacheControl.noCache());
        httpHeaders.setConnection("keep-alive");
        httpHeaders.add("Content-Encoding", "none");
        Flux<Long> interval = Flux.interval(Duration.ofSeconds(2));
        return ResponseEntity.ok()
                .headers(httpHeaders)
                .body(interval.flatMap(p -> paymentService.getNotificationsByUsername(username))
                        .doOnNext(n -> log.info("Getting notification: {}", n)));
    }

    @PostMapping(value = "/api/v1/dashboard/notifications", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("#notificationRequestDto.username() == @dashboardController.getUsername(authentication)")
    public Mono<ResponseEntity<ResponsePaymentDto>> processNotification(@Valid @RequestBody NotificationRequestDto notificationRequestDto) {
        log.debug("Processing notificationRequestDto: {}", notificationRequestDto);
        return paymentService.processNotification(notificationRequestDto)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found payment for requestId: " + notificationRequestDto.requestId())));
    }

}
