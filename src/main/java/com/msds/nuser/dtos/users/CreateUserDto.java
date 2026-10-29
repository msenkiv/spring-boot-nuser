package com.msds.nuser.dtos.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record CreateUserDto(
        @NotBlank(message = "Name is required.")
        @Size(max = 150, message = "Name must not exceed 150 characters.")
        String username,

        @NotBlank(message = "Password is required.")
        String password,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        String email) {

    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 bytes in UTF-8.")
    public boolean isPasswordWithinLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
