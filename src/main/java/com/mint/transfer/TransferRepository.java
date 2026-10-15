package com.mint.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Transfers.
 */
@Repository
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    /**
     * Looks up an existing transfer by client-provided idempotency key.
     */
    Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey);
}
