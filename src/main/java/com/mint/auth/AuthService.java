package com.mint.auth;

import com.mint.auth.dto.*;
import com.mint.common.exception.InvalidCredentialsException;
import com.mint.common.exception.InvalidTokenException;
import com.mint.common.exception.UserAlreadyExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service managing user authentication, registration, and token rotation.
 */
@Service //tells IoC to create only a singleton Bean and keep in memory for whole app lifetime
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final long refreshExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            //@Value is used here to make a default incase required key not found
            @Value("${mint.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Registers a new user account with hashed password and generates initial tokens.
     */
    // @Transactional part of Aspect Oriented Programming, auth, business, etc separated from main logic
    // ensures that all transactions are atomic and ACID compliant
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Guard against duplicate registrations
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("An account with email " + request.getEmail() + " already exists");
        }

        // 2. Hash the raw password using BCrypt (never store plain passwords in the database!)
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // 3. Save new User record
        User user = new User(
                UUID.randomUUID(),
                request.getEmail(),
                hashedPassword,
                request.getFullName(),
                "ROLE_USER"
        );
        user = userRepository.save(user);

        // 4. Generate access token and persistent refresh token
        return generateAuthResponse(user);
    }

    /**
     * Authenticates an existing user by verifying their password and issuing new tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Look up user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        // 2. Compare the raw password against the stored BCrypt hash
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // 3. Generate fresh tokens
        return generateAuthResponse(user);
    }

    /**
     * Rotates tokens using a valid refresh token.
     * Generates a new access token and a replacement refresh token, invalidating the old one.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        // 1. Locate refresh token in DB
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        // 2. Check if the token has expired
        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new InvalidTokenException("Refresh token has expired. Please log in again");
        }

        User user = storedToken.getUser();

        // 3. Delete the old refresh token (Token Rotation prevents replay attacks)
        refreshTokenRepository.delete(storedToken);

        // 4. Issue a new access token and a brand new refresh token
        return generateAuthResponse(user);
    }

    /**
     * Helper method to generate access token + new refresh token pair for a user.
     */
    private AuthResponse generateAuthResponse(User user) {
        // Create short-lived access token (JWT)
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        // Create long-lived refresh token (stored in Postgres)
        String refreshTokenString = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(this.refreshExpirationMs);

        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                refreshTokenString,
                user,
                expiresAt
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                refreshTokenString,
                jwtTokenProvider.getTokenValidityInMilliseconds() / 1000, // in seconds
                UserResponse.fromEntity(user)
        );
    }
}
