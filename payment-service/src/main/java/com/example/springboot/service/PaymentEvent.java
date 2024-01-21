package com.example.springboot.service;

import com.example.springboot.model.ResponsePaymentDto;

import java.util.Objects;

public record PaymentEvent(ResponsePaymentDto payment) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentEvent that = (PaymentEvent) o;
        return Objects.equals(payment, that.payment);
    }

}
