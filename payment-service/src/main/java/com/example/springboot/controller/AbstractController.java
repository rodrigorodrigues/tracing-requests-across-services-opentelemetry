package com.example.springboot.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

public abstract class AbstractController {
    public String getUsername(Authentication authentication) {
        String username = authentication.getName();
        if (authentication instanceof OAuth2AuthenticationToken oauth2) {
            username = oauth2.getPrincipal().getAttribute("email");
        }
        return username;
    }
}
