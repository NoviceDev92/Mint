package com.mint.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Utility component for generating and verifying JSON Web Tokens (JWT).
 *
 * HOW IT WORKS (The Intuitive Concept):
 * - Think of a JWT like a stamped wristband at an event.
 * - When a user logs in, the server stamps a wristband (signs the JWT with a secret key).
 * - For future requests, the client presents the wristband in the "Authorization: Bearer <token>" header.
 * - The server simply checks the stamp using the same secret key to confirm identity, without needing
 *   to query the database for every single HTTP request.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long tokenValidityInMilliseconds;

    public JwtTokenProvider(
            @Value("${mint.jwt.secret}") String secret,
            @Value("${mint.jwt.expiration-ms:900000}") long tokenValidityInMilliseconds) {
        // Convert the configured secret string into a cryptographic HMAC-SHA key
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityInMilliseconds = tokenValidityInMilliseconds;
    }

    /**
     * Creates a signed JWT access token containing the user's ID, email, and role.
     */
    public String generateAccessToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + this.tokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(email)                            // Standard claim: who this token belongs to
                .claim("userId", userId.toString())        // Custom claim: convenient user ID
                .claim("role", role)                       // Custom claim: user role for authorization
                .issuedAt(now)                             // When it was created
                .expiration(validity)                      // When it expires (e.g. 15 mins)
                .signWith(this.key)                        // Sign with our secret key
                .compact();
    }

    /**
     * Extracts all claims (payload data) from a token string.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(this.key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user email (the subject claim) from a token.
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validates whether a token is correctly signed and has not expired.
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            // Token is expired, signature is invalid, or string is malformed
            return false;
        }
    }

    public long getTokenValidityInMilliseconds() {
        return tokenValidityInMilliseconds;
    }
}
