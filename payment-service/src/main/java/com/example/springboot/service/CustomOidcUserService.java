package com.example.springboot.service;

import com.example.springboot.model.UserAuth;
import com.example.springboot.repository.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class CustomOidcUserService {
    private static final Logger log = LoggerFactory.getLogger(CustomOidcUserService.class);
    private final UserRepository userRepository;

    public CustomOidcUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public Mono<UserAuth> configureUser(OidcUser oidcUser) {
        return userRepository.findById(oidcUser.getEmail())
                        .switchIfEmpty(createUser(oidcUser));

    }

    private Mono<UserAuth> createUser(OidcUser oidcUser) {
        log.debug("Creating user from google oauth: {}", oidcUser.getEmail());
        String address = oidcUser.getUserInfo().getAddress().getFormatted();
        if (StringUtils.isBlank(address)) {
            address = "10 Street, Dublin, Ireland";
        }
        UserAuth userAuth = new UserAuth(oidcUser.getEmail(), new BigDecimal("1000.00"), address, null, oidcUser.getFullName());
        userAuth.setSocialMedia(true);
        return userRepository.save(userAuth);
    }
}
