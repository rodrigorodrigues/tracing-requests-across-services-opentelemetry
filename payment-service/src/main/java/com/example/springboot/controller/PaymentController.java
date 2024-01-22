package com.example.springboot.controller;

import com.example.springboot.model.RequestPaymentDto;
import com.example.springboot.model.ResponsePaymentDto;
import com.example.springboot.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController extends AbstractController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("@paymentController.checkUserPermissionResource(#requestId, authentication)")
    public Mono<ResponseEntity<ResponsePaymentDto>> getById(@PathVariable String requestId) {
        log.info("Return payment by id: " + requestId);
        return paymentService.findById(requestId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found id: " + requestId)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ResponsePaymentDto>> create(@RequestBody @Valid RequestPaymentDto requestPaymentDto,
                                                           Authentication authentication) {
        return paymentService.save(requestPaymentDto, getUsername(authentication))
                .map(p -> ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/v1/paments/"+p.requestId())).body(p))
                .onErrorResume(e -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create payment: " + e.getLocalizedMessage(), e)));
    }

    @PostMapping("/declinePayment/{requestId}")
    @PreAuthorize("@paymentController.checkUserPermissionResource(#requestId, authentication)")
    public Mono<Void> decline(@PathVariable String requestId) {
        return paymentService.declinePayment(requestId, Instant.now());
    }

    public Mono<Boolean> checkUserPermissionResource(String requestId, Authentication authentication) {
        String username = getUsername(authentication);
        log.debug("Checking if user({}) has permission to access resource", username);
        if (hasAuthority("ADMIN").check(() -> authentication, authentication).isGranted()) {
            return Mono.just(true);
        }
        return paymentService.findById(requestId)
                .map(p -> p.usernameFrom().equals(username))
                .switchIfEmpty(Mono.just(false));
    }
}
