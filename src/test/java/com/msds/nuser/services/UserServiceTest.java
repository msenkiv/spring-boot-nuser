package com.msds.nuser.services;

import com.msds.nuser.dtos.users.CreateUserDto;
import com.msds.nuser.dtos.users.UpdateUserDto;
import com.msds.nuser.entities.User;
import com.msds.nuser.exceptions.ResourceNotFoundException;
import com.msds.nuser.mappers.UserMapper;
import com.msds.nuser.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {
    @Mock
    private UserRepository userRepository;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("Deve cadastrar o usuário com senha em hash e agendar o email")
    void shouldCreateUserWithEncodedPasswordAndScheduleEmail() {
        var dto = new CreateUserDto(
                "Marvin",
                "senhaaa123",
                "marvin@email.com"
        );

        when(passwordEncoder.encode(dto.password()))
                .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);

                    assertEquals("Marvin", user.getUsername());
                    assertEquals("marvin@email.com", user.getEmail());
                    assertEquals("encodedPassword", user.getPassword());

                    user.setId(1L);
                    return user;
                });

        var result = userService.createUser(dto);

        assertEquals(1L, result.id());
        assertEquals("Marvin", result.username());
        assertEquals("marvin@email.com", result.email());

        verify(passwordEncoder).encode("senhaaa123");
        verify(userRepository).save(any(User.class));
        verifyNoInteractions(emailService);

        var callbacks =
                TransactionSynchronizationManager.getSynchronizations();

        assertEquals(1, callbacks.size());

        callbacks.forEach(TransactionSynchronization::afterCommit);

        verify(emailService)
                .sendConfirmationEmail("marvin@email.com");
    }

    @Test
    @DisplayName("Não deve enviar email quando transação do banco falhar")
    void shouldNotScheduleEmailWhenCreationFails() {
        var userDto = new CreateUserDto(
                "user",
                "###pass@@@",
                "user@email.com"
        );

        when(passwordEncoder.encode(userDto.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate email"
                ));

        assertThrows(DataIntegrityViolationException.class, () -> userService.createUser(userDto));

        verifyNoInteractions(emailService);
        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("Falha no servidor de email, cadastra o usuário e não deve propagar o erro do email - considerando eventual retentativa somente pro envio do email.")
    void shouldNotPropagateConfirmationEmailFailure() {
        var dto = new CreateUserDto(
                "Marvin",
                "plainPassword",
                "marvin@email.com"
        );

        when(passwordEncoder.encode(dto.password()))
                .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });

        doThrow(new IllegalStateException("Email unavailable"))
                .when(emailService)
                .sendConfirmationEmail(dto.email());

        var result = userService.createUser(dto);

        verifyNoInteractions(emailService);

        assertDoesNotThrow(this::executeAfterCommitCallbacks);

        assertEquals(1L, result.id());
        verify(emailService).sendConfirmationEmail(dto.email());
    }

    @Test
    @DisplayName("Deve retornar o usuário quando o ID existir")
    void shouldReturnUserWhenIdExists() {
        User user = existingUser();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        var result = userService.getUserById(1L);

        assertEquals(user.getId(), result.id());
        assertEquals(user.getUsername(), result.username());
        assertEquals(user.getEmail(), result.email());

        verifyNoInteractions(emailService, passwordEncoder);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário não existir")
    void shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.getUserById(99L)
        );

        verifyNoInteractions(emailService, passwordEncoder);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Deve listar sem filtro quando o nome não for informado")
    void shouldListUsersWithoutNameFilter(String name) {
        var pageable = PageRequest.of(0, 10);
        User user = existingUser();

        when(userRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        var result = userService.getUsers(name, pageable);

        assertEquals(1, result.content().size());
        assertEquals(user.getId(), result.content().getFirst().id());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.totalPages());

        verify(userRepository).findAll(pageable);
        verify(userRepository, never())
                .findByUsernameContainingIgnoreCase(anyString(), any());

        verifyNoInteractions(emailService, passwordEncoder);
    }

    @Test
    @DisplayName("Deve remover espaços do filtro e preservar a paginação")
    void shouldListUsersWithTrimmedNameFilter() {
        var pageable = PageRequest.of(1, 2);
        User user = existingUser();

        when(userRepository.findByUsernameContainingIgnoreCase(
                "mar",
                pageable
        )).thenReturn(new PageImpl<>(List.of(user), pageable, 3));

        var result = userService.getUsers("  mar  ", pageable);

        assertEquals(1, result.content().size());
        assertEquals("Marvin", result.content().getFirst().username());
        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(3L, result.totalElements());
        assertEquals(2, result.totalPages());

        verify(userRepository)
                .findByUsernameContainingIgnoreCase("mar", pageable);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("Deve retornar página vazia quando nenhum usuário corresponder")
    void shouldReturnEmptyPageWhenNoUsersMatch() {
        var pageable = PageRequest.of(0, 10);

        when(userRepository.findByUsernameContainingIgnoreCase(
                "unknown",
                pageable
        )).thenReturn(Page.empty(pageable));

        var result = userService.getUsers("unknown", pageable);

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(0L, result.totalElements());
        assertEquals(0, result.totalPages());
    }

    @Test
    @DisplayName("Deve alterar somente o nome e agendar o email de atualização")
    void shouldUpdateOnlyUsernameAndScheduleEmail() {
        User user = existingUser();
        String previousPassword = user.getPassword();
        String previousEmail = user.getEmail();

        var dto = new UpdateUserDto("New Name", null);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenReturn(user);

        var result = userService.updateUser(1L, dto);

        assertEquals("New Name", user.getUsername());
        assertEquals("New Name", result.username());
        assertEquals(previousEmail, result.email());
        assertEquals(previousEmail, user.getEmail());
        assertEquals(previousPassword, user.getPassword());

        verify(userRepository).save(user);
        verifyNoInteractions(passwordEncoder, emailService);

        executeAfterCommitCallbacks();

        verify(emailService).sendUpdatedInformationEmail(previousEmail);
        verify(emailService, never()).sendConfirmationEmail(anyString());
    }

    @Test
    @DisplayName("Deve alterar somente a senha e salvar seu hash")
    void shouldUpdateOnlyPassword() {
        User user = existingUser();
        String previousUsername = user.getUsername();
        String previousEmail = user.getEmail();

        var dto = new UpdateUserDto(null, "newPassword");

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode(dto.password()))
                .thenReturn("newEncodedPassword");

        when(userRepository.save(user))
                .thenReturn(user);

        var result = userService.updateUser(1L, dto);

        assertEquals(previousUsername, user.getUsername());
        assertEquals(previousUsername, result.username());
        assertEquals(previousEmail, user.getEmail());
        assertEquals(previousEmail, result.email());
        assertEquals("newEncodedPassword", user.getPassword());

        verify(passwordEncoder).encode(dto.password());
        verify(userRepository).save(user);
        verifyNoInteractions(emailService);

        executeAfterCommitCallbacks();

        verify(emailService).sendUpdatedInformationEmail(previousEmail);
    }

    @Test
    @DisplayName("Não deve salvar nem agendar email ao editar usuário inexistente")
    void shouldNotUpdateMissingUser() {
        var dto = new UpdateUserDto("New Name", null);

        when(userRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> userService.updateUser(99L, dto)
        );

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(emailService, passwordEncoder);

        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    @Test
    @DisplayName("Não deve agendar email quando a edição falhar no repository")
    void shouldNotScheduleEmailWhenUpdateFails() {
        User user = existingUser();
        var dto = new UpdateUserDto("New Name", null);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(user))
                .thenThrow(new DataIntegrityViolationException(
                        "Persistence failed"
                ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userService.updateUser(1L, dto)
        );

        verifyNoInteractions(emailService);

        assertTrue(
                TransactionSynchronizationManager
                        .getSynchronizations()
                        .isEmpty()
        );
    }

    private User existingUser() {
        User user = new User(
                "marvin@email.com",
                "Marvin",
                "senhaantiga"
        );
        user.setId(1L);
        return user;
    }

    private void executeAfterCommitCallbacks() {
        var callbacks =
                TransactionSynchronizationManager.getSynchronizations();

        assertEquals(1, callbacks.size());

        callbacks.forEach(TransactionSynchronization::afterCommit);
    }


}
