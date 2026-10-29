package com.msds.nuser.dtos.users;

public record UserResponseDto(
        Long id,
        String username,
        String email
) {}