package com.msds.nuser.dtos.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public record UpdateUserDto(
        @Size(max = 150, message = "Name must not exceed 150 characters.")
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "Name must not be blank."
        )
        String username,

        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "Password must not be blank."
        )
        String password
) {

    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 bytes in UTF-8.")
    public boolean isPasswordWithinLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
