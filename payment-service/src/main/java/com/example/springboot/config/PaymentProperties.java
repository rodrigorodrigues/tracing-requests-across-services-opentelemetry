package com.example.springboot.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "com.example")
public class PaymentProperties {
    @NotNull
    private long expirePaymentInSecs = 120;

    public void setExpirePaymentInSecs(long expirePaymentInSecs) {
        this.expirePaymentInSecs = expirePaymentInSecs;
    }

    public long getExpirePaymentInSecs() {
        return expirePaymentInSecs;
    }
}
