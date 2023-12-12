package com.example.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;

    private final UserRepository userRepository;

    PaymentService(PaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getAllByUsernameFrom(String usernameFrom) {
        return userRepository.findById(usernameFrom)
                .map(u -> {
                    List<PaymentDto> payments = new ArrayList<>();
                    for (Payment payment : u.getPayments()) {
                        payments.add(new PaymentDto(payment));
                    }
                    return payments;
                })
                .orElseGet(Collections::emptyList);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentDto> findById(String paymentId) {
        return paymentRepository.findPaymentByPaymentId(paymentId);
    }

    @Transactional
    public PaymentDto save(PaymentDto paymentDto) {
        Payment payment = new Payment(paymentDto, findByUser(paymentDto.usernameFrom()), findByUser(paymentDto.usernameTo()));
        payment = paymentRepository.save(payment);
        paymentDto = new PaymentDto(payment);
        log.info("paymentDto: {}", paymentDto);
        return paymentDto;
    }

    private User findByUser(String username) {
        return userRepository.findById(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Not found username: " + username));
    }

    public void declinePayment(String paymentId) {
        paymentRepository.declinePayment(PaymentStatus.DECLINED, paymentId);
    }
}
