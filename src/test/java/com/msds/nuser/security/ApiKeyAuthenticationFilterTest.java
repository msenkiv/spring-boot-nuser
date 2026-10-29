package com.msds.nuser.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthenticationFilter")
class ApiKeyAuthenticationFilterTest {

    private static final String API_KEY = "test-api-key";

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter = new ApiKeyAuthenticationFilter(API_KEY);
        request = new MockHttpServletRequest("GET", "/users");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve autenticar e seguir a requisição quando a chave for válida")
    void shouldAuthenticateWithValidApiKey() throws Exception {
        request.addHeader("X-API-Key", API_KEY);

        doAnswer(invocation -> {
            var authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            assertNotNull(authentication);
            assertTrue(authentication.isAuthenticated());
            assertEquals("api-client", authentication.getPrincipal());
            assertNull(authentication.getCredentials());

            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    @Test
    @DisplayName("Deve retornar 401 quando a chave não for enviada")
    void shouldRejectMissingApiKey() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertUnauthorized();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-key", "", "   "})
    @DisplayName("Deve retornar 401 quando a chave enviada for inválida")
    void shouldRejectInvalidApiKey(String apiKey) throws Exception {
        request.addHeader("X-API-Key", apiKey);

        filter.doFilter(request, response, filterChain);

        assertUnauthorized();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Deve impedir a criação do filtro sem uma chave configurada")
    void shouldRejectInvalidConfiguredKey(String apiKey) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApiKeyAuthenticationFilter(apiKey)
        );
    }

    @Test
    @DisplayName("Deve limpar a autenticação mesmo quando o próximo filtro falhar")
    void shouldClearContextWhenFilterChainFails() throws Exception {
        request.addHeader("X-API-Key", API_KEY);

        doAnswer(invocation -> {
            assertNotNull(
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication()
            );

            throw new ServletException("Request processing failed");
        }).when(filterChain).doFilter(request, response);

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, filterChain)
        );

        verify(filterChain).doFilter(request, response);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    private void assertUnauthorized() throws Exception {
        assertEquals(401, response.getStatus());
        assertTrue(
                MediaType.APPLICATION_JSON.isCompatibleWith(
                        MediaType.parseMediaType(response.getContentType())
                )
        );

        assertEquals(
                "{\"status\":401,\"data\":null}",
                response.getContentAsString().strip()
        );

        verifyNoInteractions(filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }
}