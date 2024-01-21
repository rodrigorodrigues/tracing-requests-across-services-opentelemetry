package com.example.springboot.controller;

import com.example.springboot.model.UserAuth;
import com.example.springboot.model.UserRequestDto;
import com.example.springboot.model.UserResponseDto;
import com.example.springboot.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
public class AccountController extends AbstractController {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public AccountController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/api/v1/account")
    public Mono<ResponseEntity<UserResponseDto>> index(Authentication authentication) {
        String username = getUsername(authentication);
        return userRepository.findById(username)
                .map(UserResponseDto::new)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Username not found: %s", username))));
    }

    @PostMapping("/api/v1/users/register")
    public Mono<ResponseEntity<UserResponseDto>> registerUser(@RequestBody @Valid UserRequestDto userRequestDto) {
        if (!userRequestDto.password().equals(userRequestDto.confirmPassword())) {
            return Mono.error(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords don't match"));
        }
        return userRepository.findById(userRequestDto.username())
                .flatMap(u -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists: "+u.getUsername())))
                .then(Mono.just(userRequestDto)
                            .map(u -> new UserAuth(u, passwordEncoder.encode(u.password())))
                            .flatMap(userRepository::save)
                            .map(UserResponseDto::new)
                            .map(p -> ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/account")).body(p))
                            .onErrorResume(ex -> Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create user: " + ex.getLocalizedMessage(), ex)))
                );
    }
}
