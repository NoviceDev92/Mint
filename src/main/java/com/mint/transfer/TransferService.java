package com.mint.transfer;

import com.mint.account.Account;
import com.mint.account.AccountRepository;
import com.mint.auth.User;
import com.mint.auth.UserRepository;
import com.mint.common.exception.*;
import com.mint.ledger.Direction;
import com.mint.ledger.LedgerEntry;
import com.mint.ledger.LedgerEntryRepository;
import com.mint.transfer.dto.TransferRequest;
import com.mint.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Core Transfer engine implementing double-entry bookkeeping,
 * deterministic pessimistic locking (deadlock prevention), and idempotent request processing.
 */
@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;

    public TransferService(AccountRepository accountRepository,
                           TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Executes a financial transfer with full idempotency, row-level locking, and double-entry accounting.
     */
    @Transactional
    public TransferResponse executeTransfer(String userEmail, UUID idempotencyKey, TransferRequest request) {
        // 1. Idempotency Check: Safely return existing transaction if client retried
        Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            return TransferResponse.fromEntity(existingTransfer.get());
        }

        // 2. Pre-flight Validation
        UUID sourceId = request.getSourceAccountId();
        UUID destId = request.getDestinationAccountId();

        if (sourceId.equals(destId)) {
            throw new SelfTransferNotAllowedException("Source and destination accounts cannot be identical");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // 3. Deadlock Prevention via Deterministic Lock Ordering
        // By always acquiring locks in lexical UUID order (min UUID first, max UUID second),
        // we guarantee two concurrent cross-transfers (A->B and B->A) never deadlock waiting for each other.
        UUID firstLockId = sourceId.compareTo(destId) < 0 ? sourceId : destId;
        UUID secondLockId = sourceId.compareTo(destId) < 0 ? destId : sourceId;

        Account firstAccount = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + firstLockId));
        Account secondAccount = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + secondLockId));

        Account sourceAccount = firstAccount.getId().equals(sourceId) ? firstAccount : secondAccount;
        Account destinationAccount = firstAccount.getId().equals(destId) ? firstAccount : secondAccount;

        // 4. Security Authorization Check: Verify caller owns the source account
        if (!sourceAccount.getUserId().equals(user.getId()) && !"ROLE_ADMIN".equals(user.getRole())) {
            throw new UnauthorizedAccountAccessException("You do not have permission to debit from account: " + sourceId);
        }

        // 5. Account State & Currency Validation
        if (sourceAccount.isFrozen()) {
            throw new AccountFrozenException("Source account is frozen and cannot transfer funds: " + sourceId);
        }
        if (destinationAccount.isFrozen()) {
            throw new AccountFrozenException("Destination account is frozen and cannot receive funds: " + destId);
        }

        String requestCurrency = request.getCurrency().toUpperCase();
        if (!sourceAccount.getCurrency().equalsIgnoreCase(destinationAccount.getCurrency()) ||
                !sourceAccount.getCurrency().equalsIgnoreCase(requestCurrency)) {
            throw new CurrencyMismatchException(String.format(
                    "Currency mismatch: source is %s, destination is %s, requested was %s",
                    sourceAccount.getCurrency(), destinationAccount.getCurrency(), requestCurrency));
        }

        // 6. Balance Check
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds: available balance is %s, requested transfer amount is %s",
                    sourceAccount.getBalance(), request.getAmount()));
        }

        // 7. Persist Initial Transfer Record
        Transfer transfer = new Transfer(
                UUID.randomUUID(),
                idempotencyKey,
                sourceId,
                destId,
                request.getAmount(),
                requestCurrency
        );
        transfer = transferRepository.save(transfer);

        // 8. Execute Double-Entry Balance Updates
        sourceAccount.debit(request.getAmount());
        destinationAccount.credit(request.getAmount());
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // 9. Append Immutable Double-Entry Ledger Lines
        // Entry 1: Source Account DEBIT
        LedgerEntry debitEntry = new LedgerEntry(
                UUID.randomUUID(),
                sourceAccount,
                transfer,
                Direction.DEBIT,
                request.getAmount()
        );
        ledgerEntryRepository.save(debitEntry);

        // Entry 2: Destination Account CREDIT
        LedgerEntry creditEntry = new LedgerEntry(
                UUID.randomUUID(),
                destinationAccount,
                transfer,
                Direction.CREDIT,
                request.getAmount()
        );
        ledgerEntryRepository.save(creditEntry);

        // 10. Mark Transfer as COMPLETED
        transfer.markCompleted();
        transfer = transferRepository.save(transfer);

        return TransferResponse.fromEntity(transfer);
    }

    /**
     * Retrieves the status and details of a transfer by ID.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransferById(UUID transferId, String userEmail) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found with ID: " + transferId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        // Check ownership against source or destination account
        Account source = accountRepository.findById(transfer.getSourceAccountId()).orElse(null);
        Account dest = accountRepository.findById(transfer.getDestinationAccountId()).orElse(null);

        boolean isParticipant = (source != null && source.getUserId().equals(user.getId())) ||
                                (dest != null && dest.getUserId().equals(user.getId()));

        if (!isParticipant && !"ROLE_ADMIN".equals(user.getRole())) {
            throw new UnauthorizedAccountAccessException("You do not have permission to view this transfer");
        }

        return TransferResponse.fromEntity(transfer);
    }
}
