package com.mint.account;

import com.mint.account.dto.AccountResponse;
import com.mint.account.dto.CreateAccountRequest;
import com.mint.ledger.dto.LedgerEntryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for account and wallet lifecycle endpoints.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Creates a new financial account / wallet for the authenticated user.
     * POST /api/v1/accounts
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(email, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves all accounts belonging to the authenticated user.
     * GET /api/v1/accounts/me
     */
    @GetMapping("/me")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @AuthenticationPrincipal String email) {
        List<AccountResponse> accounts = accountService.getMyAccounts(email);
        return ResponseEntity.ok(accounts);
    }

    /**
     * Retrieves specific account details by account ID.
     * GET /api/v1/accounts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id) {
        AccountResponse account = accountService.getAccountById(id, email);
        return ResponseEntity.ok(account);
    }

    /**
     * Retrieves paginated ledger statements / transaction lines for an account.
     * GET /api/v1/accounts/{id}/entries
     */
    @GetMapping("/{id}/entries")
    public ResponseEntity<Page<LedgerEntryResponse>> getAccountEntries(
            @AuthenticationPrincipal String email,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<LedgerEntryResponse> entries = accountService.getAccountEntries(id, email, pageable);
        return ResponseEntity.ok(entries);
    }
}
