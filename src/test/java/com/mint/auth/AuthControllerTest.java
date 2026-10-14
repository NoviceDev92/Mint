package com.mint.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mint.auth.dto.AuthResponse;
import com.mint.auth.dto.LoginRequest;
import com.mint.auth.dto.RegisterRequest;
import com.mint.auth.dto.UserResponse;
import com.mint.common.GlobalExceptionHandler;
import com.mint.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer test for AuthController.
 * Uses MockMvc to test HTTP request/response serialization, validation, and status codes.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("POST /api/v1/auth/register returns 201 Created on valid input")
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest("alice@example.com", "password123", "Alice Smith");

        UserResponse userResponse = new UserResponse(
                UUID.randomUUID(),
                request.getEmail(),
                request.getFullName(),
                "ROLE_USER",
                Instant.now()
        );
        AuthResponse authResponse = new AuthResponse("mock-access-token", "mock-refresh-token", 900, userResponse);

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token"))
                .andExpect(jsonPath("$.user.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register returns 400 Bad Request on invalid email")
    void shouldFailRegisterWhenEmailIsInvalid() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest("not-an-email", "password123", "Alice Smith");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK on valid credentials")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest("alice@example.com", "password123");

        UserResponse userResponse = new UserResponse(
                UUID.randomUUID(),
                request.getEmail(),
                "Alice Smith",
                "ROLE_USER",
                Instant.now()
        );
        AuthResponse authResponse = new AuthResponse("mock-access-token", "mock-refresh-token", 900, userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }
}
