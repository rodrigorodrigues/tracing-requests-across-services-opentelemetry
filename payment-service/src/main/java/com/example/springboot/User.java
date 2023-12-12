package com.example.springboot;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "tb_user")
public class User {
    @Id
    @NotBlank
    private String username;
    @NotNull
    private BigDecimal balance = BigDecimal.ZERO;
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY, mappedBy = "usernameFrom")
    private List<Payment> payments;

    @NotNull
    private Locale locale;

    private boolean active = true;

    public User(){}

    public User(String username, BigDecimal balance, Locale locale) {
        this.username = username;
        this.balance = balance;
        this.locale = locale;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", balance=" + balance +
                '}';
    }
}
