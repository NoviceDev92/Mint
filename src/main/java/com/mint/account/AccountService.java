package com.mint.account;

import com.mint.account.dto.AccountResponse;
import com.mint.account.dto.CreateAccountRequest;
import com.mint.auth.User;
import com.mint.auth.UserRepository;
import com.mint.common.exception.ResourceNotFoundException;
import com.mint.common.exception.UnauthorizedAccountAccessException;
import com.mint.ledger.LedgerEntryRepository;
import com.mint.ledger.dto.LedgerEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service managing user wallets and financial accounts.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Creates a new account for the authenticated user.
     */
    @Transactional
    public AccountResponse createAccount(String userEmail, CreateAccountRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        BigDecimal initialBalance = request.getInitialDeposit() != null
                ? request.getInitialDeposit()
                : BigDecimal.ZERO;

        Account account = new Account(
                UUID.randomUUID(),
                user.getId(),
                user.getFullName(),
                request.getCurrency().toUpperCase(),
                initialBalance
        );

        Account saved = accountRepository.save(account);
        return AccountResponse.fromEntity(saved);
    }

    /**
     * Retrieves account details by ID, validating that the caller owns the account.
     */
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId, String userEmail) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateOwnership(account, userEmail);

        return AccountResponse.fromEntity(account);
    }

    /**
     * Retrieves all accounts owned by the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(AccountResponse::fromEntity)
                .toList();
    }

    /**
     * Returns paginated ledger entries (statement lines) for an account.
     */
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountEntries(UUID accountId, String userEmail, Pageable pageable) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        validateOwnership(account, userEmail);

        return ledgerEntryRepository.findByAccountId(accountId, pageable)
                .map(LedgerEntryResponse::fromEntity);
    }

    private void validateOwnership(Account account, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userEmail));

        if (!account.getUserId().equals(user.getId()) && !"ROLE_ADMIN".equals(user.getRole())) {
            throw new UnauthorizedAccountAccessException("You do not have permission to view or manage this account");
        }
    }
}
