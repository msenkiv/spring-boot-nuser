package com.msds.nuser.mappers;

import com.msds.nuser.dtos.users.CreateUserDto;
import com.msds.nuser.dtos.users.UserResponseDto;
import com.msds.nuser.entities.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public User toEntity(CreateUserDto dto, String encodedPassword) {
        return new User(
                dto.email(),
                dto.username(),
                encodedPassword
        );
    }

    public UserResponseDto toResponse(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }
}
