package com.msds.nuser.services;

import com.msds.nuser.dtos.users.CreateUserDto;
import com.msds.nuser.dtos.PageResponseDto;
import com.msds.nuser.dtos.users.UpdateUserDto;
import com.msds.nuser.dtos.users.UserResponseDto;
import com.msds.nuser.entities.User;
import com.msds.nuser.exceptions.ResourceNotFoundException;
import com.msds.nuser.mappers.UserMapper;
import com.msds.nuser.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class UserService {
    private static final Logger logger =
            LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailService emailService;

    @Transactional
    public UserResponseDto createUser(CreateUserDto createUserDto) {
        String encodedPassword = this.passwordEncoder.encode(createUserDto.password());

        User user = this.userMapper.toEntity(createUserDto, encodedPassword);
        User savedUser = this.userRepository.save(user);

        String email = savedUser.getEmail();
        afterCommit(() -> emailService.sendConfirmationEmail(email));
        return userMapper.toResponse(savedUser);

    }

    public UserResponseDto getUserById(Long userId) {
        User user = this.userRepository
                .findById(userId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toResponse(user);
    }

    public PageResponseDto<UserResponseDto> getUsers(
            String name,
            Pageable pageable) {
        Page<User> usersPage;

        if (name == null || name.isBlank()) {
            usersPage = userRepository.findAll(pageable);
        } else {
            usersPage = userRepository.findByUsernameContainingIgnoreCase(
                    name.strip(),
                    pageable
            );
        }
        Page<UserResponseDto> usersPageResponse = usersPage.map(userMapper::toResponse);
        return new PageResponseDto<>(
                usersPageResponse.getContent(),
                usersPageResponse.getNumber(),
                usersPageResponse.getSize(),
                usersPageResponse.getTotalElements(),
                usersPageResponse.getTotalPages()
        );
    }

    @Transactional
    public UserResponseDto updateUser(Long userId, UpdateUserDto updateUserDto) {
        User user = this.userRepository
                .findById(userId)
                .orElseThrow(()-> new ResourceNotFoundException("User not found with id: " + userId));

        if(updateUserDto.username() != null) {
            user.setUsername(updateUserDto.username());
        }

        if(updateUserDto.password() != null) {
            user.setPassword(passwordEncoder.encode(updateUserDto.password()));
        }

        User savedUser = this.userRepository.save(user);
        String email = savedUser.getEmail();
        afterCommit(() -> emailService.sendUpdatedInformationEmail(email));
        return userMapper.toResponse(savedUser);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            action.run();
                        } catch (Exception exception) {
                            logger.error(
                                    "Email failed after the transaction was committed.",
                                    exception
                            );
                        }
                    }
                }
        );
    }
}
