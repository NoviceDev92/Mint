package com.mint.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for LedgerEntry.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Retrieves paginated ledger statements for an account ordered by newest first.
     */
    @Query("SELECT e FROM LedgerEntry e WHERE e.account.id = :accountId ORDER BY e.createdAt DESC")
    Page<LedgerEntry> findByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}
