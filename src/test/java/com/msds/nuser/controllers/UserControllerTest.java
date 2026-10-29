package com.msds.nuser.controllers;

import com.msds.nuser.dtos.PageResponseDto;
import com.msds.nuser.dtos.users.CreateUserDto;
import com.msds.nuser.dtos.users.UpdateUserDto;
import com.msds.nuser.dtos.users.UserResponseDto;
import com.msds.nuser.exceptions.GlobalExceptionHandler;
import com.msds.nuser.exceptions.ResourceNotFoundException;
import com.msds.nuser.services.UserService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver()
                )
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    @DisplayName("Deve cadastrar e retornar 201 com os dados sem senha")
    void shouldCreateUser() throws Exception {
        var dto = new CreateUserDto(
                "Marvin",
                "password123",
                "marvin@email.com"
        );

        when(userService.createUser(dto))
                .thenReturn(userResponse());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "Marvin",
                                  "password": "password123",
                                  "email": "marvin@email.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("Marvin"))
                .andExpect(jsonPath("$.data.email")
                        .value("marvin@email.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userService).createUser(dto);
    }

    @ParameterizedTest
    @MethodSource("invalidCreationRequests")
    @DisplayName("Deve retornar 400 e não chamar o service no cadastro inválido")
    void shouldRejectInvalidCreation(
            String requestBody,
            String expectedMessage
    ) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.message")
                        .value(expectedMessage));

        verifyNoInteractions(userService);
    }

    static Stream<Arguments> invalidCreationRequests() {
        return Stream.of(
                Arguments.of(
                        creationJson("", "password123", "marvin@email.com"),
                        "Name is required."
                ),
                Arguments.of(
                        creationJson(
                                "a".repeat(151),
                                "password123",
                                "marvin@email.com"
                        ),
                        "Name must not exceed 150 characters."
                ),
                Arguments.of(
                        creationJson("Marvin", "", "marvin@email.com"),
                        "Password is required."
                ),
                Arguments.of(
                        creationJson(
                                "Marvin",
                                "a".repeat(73),
                                "marvin@email.com"
                        ),
                        "Password must not exceed 72 bytes in UTF-8."
                ),
                Arguments.of(
                        creationJson("Marvin", "é".repeat(37), "marvin@email.com"),
                        "Password must not exceed 72 bytes in UTF-8."
                ),
                Arguments.of(
                        creationJson("Marvin", "password123", "invalid-email"),
                        "Email must be valid."
                ),
                Arguments.of(
                        creationJson("Marvin", "password123", ""),
                        "Email is required."
                )
        );
    }

    @Test
    @DisplayName("Deve retornar 409 quando o email já estiver cadastrado")
    void shouldReturnConflictForDuplicateEmail() throws Exception {
        var violation = mock(ConstraintViolationException.class);

        when(violation.getConstraintName())
                .thenReturn("users_email_key");

        when(userService.createUser(any(CreateUserDto.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate email",
                        violation
                ));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationJson(
                                "Marvin",
                                "password123",
                                "marvin@email.com"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.data.message")
                        .value("Email already registered."));
    }

    @Test
    @DisplayName("Deve consultar por ID e retornar 200 sem senha")
    void shouldGetUserById() throws Exception {
        when(userService.getUserById(1L))
                .thenReturn(userResponse());

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("Marvin"))
                .andExpect(jsonPath("$.data.email")
                        .value("marvin@email.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userService).getUserById(1L);
    }

    @Test
    @DisplayName("Deve retornar 404 quando o usuário consultado não existir")
    void shouldReturnNotFound() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException(
                        "User not found with id: 99"
                ));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.data.message")
                        .value("User not found with id: 99"));
    }

    @Test
    @DisplayName("Deve listar com filtro e encaminhar os parâmetros de paginação")
    void shouldListUsersWithFilterAndPagination() throws Exception {
        var response = new PageResponseDto<>(
                List.of(userResponse()),
                1,
                2,
                3L,
                2
        );

        when(userService.getUsers(eq("mar"), any(Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/users")
                        .param("name", "mar")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].password")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        var captor = ArgumentCaptor.forClass(Pageable.class);

        verify(userService).getUsers(eq("mar"), captor.capture());

        Pageable pageable = captor.getValue();

        assertEquals(1, pageable.getPageNumber());
        assertEquals(2, pageable.getPageSize());
        assertEquals(
                Sort.by("id"),
                pageable.getSort()
        );
    }

    @Test
    @DisplayName("Deve usar a paginação padrão quando não houver parâmetros")
    void shouldUseDefaultPagination() throws Exception {
        var response = new PageResponseDto<UserResponseDto>(
                List.of(),
                0,
                20,
                0L,
                0
        );

        when(userService.getUsers(isNull(), any(Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        var captor = ArgumentCaptor.forClass(Pageable.class);

        verify(userService).getUsers(isNull(), captor.capture());

        Pageable pageable = captor.getValue();

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.by("id"), pageable.getSort());
    }

    @Test
    @DisplayName("Deve permitir atualizar somente o nome")
    void shouldUpdateOnlyUsername() throws Exception {
        var dto = new UpdateUserDto("Updated Name", null);

        when(userService.updateUser(1L, dto))
                .thenReturn(new UserResponseDto(
                        1L,
                        "Updated Name",
                        "marvin@email.com"
                ));

        mockMvc.perform(patch("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "Updated Name"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.username")
                        .value("Updated Name"))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userService).updateUser(1L, dto);
    }

    @Test
    @DisplayName("Deve permitir atualizar somente a senha sem retorná-la")
    void shouldUpdateOnlyPassword() throws Exception {
        var dto = new UpdateUserDto(null, "newPassword123");

        when(userService.updateUser(1L, dto))
                .thenReturn(userResponse());

        mockMvc.perform(patch("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "newPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        verify(userService).updateUser(1L, dto);
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateRequests")
    @DisplayName("Deve retornar 400 quando um campo da edição for inválido")
    void shouldRejectInvalidUpdate(
            String requestBody,
            String expectedMessage
    ) throws Exception {
        mockMvc.perform(patch("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data.message")
                        .value(expectedMessage));

        verifyNoInteractions(userService);
    }

    static Stream<Arguments> invalidUpdateRequests() {
        return Stream.of(
                Arguments.of(
                        "{\"password\":\"" + "a".repeat(73) + "\"}",
                        "Password must not exceed 72 bytes in UTF-8."
                ),
                Arguments.of(
                        "{\"password\":\"" + "é".repeat(37) + "\"}",
                        "Password must not exceed 72 bytes in UTF-8."
                ),
                Arguments.of(
                        """
                        {"username": "   "}
                        """,
                        "Name must not be blank."
                ),
                Arguments.of(
                        """
                        {"password": "   "}
                        """,
                        "Password must not be blank."
                ),
                Arguments.of(
                        "{\"username\":\"" + "a".repeat(151) + "\"}",
                        "Name must not exceed 150 characters."
                )
        );
    }

    @Test
    @DisplayName("Deve retornar 404 ao editar um usuário inexistente")
    void shouldReturnNotFoundWhenUpdatingMissingUser() throws Exception {
        when(userService.updateUser(
                eq(99L),
                any(UpdateUserDto.class)
        )).thenThrow(new ResourceNotFoundException(
                "User not found with id: 99"
        ));

        mockMvc.perform(patch("/users/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Updated Name"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.data.message")
                        .value("User not found with id: 99"));
    }

    @Test
    @DisplayName("Deve retornar 500 sem expor detalhes internos")
    void shouldHandleUnexpectedFailure() throws Exception {
        when(userService.getUserById(1L))
                .thenThrow(new IllegalStateException(
                        "Internal database details"
                ));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.data.message")
                        .value("An unexpected error occurred."));
    }

    @ParameterizedTest
    @MethodSource("passwordsAtByteLimit")
    @DisplayName("Deve aceitar senha de exatamente 72 bytes no cadastro")
    void shouldAcceptPasswordAtByteLimitOnCreation(String password) throws Exception {
        var dto = new CreateUserDto("Marvin", password, "marvin@email.com");

        when(userService.createUser(dto)).thenReturn(userResponse());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(creationJson(dto.username(), password, dto.email())))
                .andExpect(status().isCreated());

        verify(userService).createUser(dto);
    }

    @ParameterizedTest
    @MethodSource("passwordsAtByteLimit")
    @DisplayName("Deve aceitar senha de exatamente 72 bytes na edição")
    void shouldAcceptPasswordAtByteLimitOnUpdate(String password) throws Exception {
        var dto = new UpdateUserDto(null, password);

        when(userService.updateUser(1L, dto)).thenReturn(userResponse());

        mockMvc.perform(patch("/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk());

        verify(userService).updateUser(1L, dto);
    }

    static Stream<String> passwordsAtByteLimit() {
        return Stream.of("a".repeat(72), "é".repeat(36));
    }

    private UserResponseDto userResponse() {
        return new UserResponseDto(
                1L,
                "Marvin",
                "marvin@email.com"
        );
    }

    private static String creationJson(
            String username,
            String password,
            String email
    ) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "email": "%s"
                }
                """.formatted(username, password, email);
    }
}
