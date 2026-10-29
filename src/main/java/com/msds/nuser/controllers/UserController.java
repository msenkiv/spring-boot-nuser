package com.msds.nuser.controllers;

import com.msds.nuser.dtos.ApiResponseDto;
import com.msds.nuser.dtos.users.CreateUserDto;
import com.msds.nuser.dtos.PageResponseDto;
import com.msds.nuser.dtos.users.UpdateUserDto;
import com.msds.nuser.dtos.users.UserResponseDto;
import com.msds.nuser.services.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponseDto<UserResponseDto>> createUser(@Valid @RequestBody CreateUserDto createUserDto){
        UserResponseDto responseDto = userService.createUser(createUserDto);
        HttpStatus status = HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(status.value(), responseDto));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> getUserById(
            @PathVariable("id") Long userId
    ){
        UserResponseDto responseDto = userService.getUserById(userId);
        HttpStatus status = HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(status.value(), responseDto));
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<UserResponseDto>>> getUsers(
            @RequestParam(name = "name", required = false) String name,
            @PageableDefault(size = 20, sort = "id") Pageable pageable
    ){
        PageResponseDto<UserResponseDto> users =
                userService.getUsers(name, pageable);

        HttpStatus status = HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(status.value(), users));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponseDto<UserResponseDto>> updateUser(
            @PathVariable("id") Long userId,
            @Valid @RequestBody UpdateUserDto updateUserDto
    ) {
        UserResponseDto user = userService.updateUser(userId, updateUserDto);
        HttpStatus status = HttpStatus.OK;

        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(status.value(), user));
    }
}
