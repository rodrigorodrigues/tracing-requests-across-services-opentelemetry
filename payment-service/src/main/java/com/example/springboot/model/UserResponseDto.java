package com.example.springboot.model;

import org.springframework.security.core.GrantedAuthority;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

public record UserResponseDto(String username, String fullName, BigDecimal balance, List<String> permissions) {
    public UserResponseDto(UserAuth userAuth) {
        this(userAuth.getUsername(),
                userAuth.getFullName(),
                userAuth.getBalance().setScale(2, RoundingMode.HALF_UP),
                userAuth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
    }
}
