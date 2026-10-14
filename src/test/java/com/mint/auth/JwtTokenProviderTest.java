package com.mint.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 * Verifies token generation, claims extraction, and validation without needing a database.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "test-super-secret-key-that-is-at-least-32-bytes-long-for-testing!";
    private final long validityMs = 60000; // 1 minute

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, validityMs);
    }

    @Test
    @DisplayName("Should generate valid token and extract email correctly")
    void shouldGenerateAndExtractEmail() {
        UUID userId = UUID.randomUUID();
        String email = "john.doe@example.com";
        String role = "ROLE_USER";

        String token = jwtTokenProvider.generateAccessToken(userId, email, role);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
    }

    @Test
    @DisplayName("Should reject invalid or tampered token")
    void shouldRejectInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.string"));
    }
}
