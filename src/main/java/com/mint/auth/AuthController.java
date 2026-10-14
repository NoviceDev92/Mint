package com.mint.auth;

import com.mint.auth.dto.AuthResponse;
import com.mint.auth.dto.LoginRequest;
import com.mint.auth.dto.RefreshTokenRequest;
import com.mint.auth.dto.RegisterRequest;
import com.mint.auth.dto.UserResponse;
import com.mint.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing authentication and user lifecycle endpoints.
 */

//@RestController is a combination of two:
//@Controller -> says that this class will handle web requests
//@ResponseBody -> convert return value to JSON, never render HTML
@RestController
@RequestMapping("/api/v1/auth") //base URL
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user account.
     * POST /api/v1/auth/register
     */

    //ResponseEntity<T> helps control HTTP responses -> return status codes, headers, body

    @PostMapping("/register") //maps incoming POST to /register (base URL + method annotation = /api/v1/auth/register)

    // @Valid checks login request for validation rules and immediately returns 400 Bad Request if issues
    // @RequestBody intercepts JSON and maps to Java object
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Authenticates an existing user and returns access + refresh tokens.
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a new access token using a valid refresh token.
     * POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Protected endpoint to verify JWT authentication works.
     * Returns the currently logged in user's profile.
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(UserResponse.fromEntity(user));
    }
}
