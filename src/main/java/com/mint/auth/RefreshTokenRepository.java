package com.mint.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for refresh token persistence and revocation.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Looks up a refresh token record by its token string.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Deletes a refresh token record (e.g. during logout or token rotation).
     */
    void deleteByToken(String token);

    /**
     * Deletes all refresh tokens for a specific user.
     */
    void deleteByUser(User user);
}
