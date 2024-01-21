package com.example.springboot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRequestDto(@Size(min = 4, max = 200) @NotBlank String username,
                             @Size(min = 4, max = 200)
                             @NotBlank String fullName,
                             @Pattern(regexp = "^(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{4,}$", message = " Password must be at least 4 characters, at least one upper case letter, one lower case letter, and one numeric digit.")
                             @NotBlank  String password,
                             @NotBlank String confirmPassword,
                             @Size(min = 10, max = 200)
                             @NotBlank String address) {
}
