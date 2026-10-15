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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransferService transferService;

    private User alice;
    private User bob;
    private Account sourceAccount;
    private Account destAccount;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        alice = new User(UUID.randomUUID(), "alice@mint.internal", "hashed", "Alice Doe", "ROLE_USER");
        bob = new User(UUID.randomUUID(), "bob@mint.internal", "hashed", "Bob Smith", "ROLE_USER");

        sourceAccount = new Account(UUID.randomUUID(), alice.getId(), "Alice Doe", "INR", new BigDecimal("1000.0000"));
        destAccount = new Account(UUID.randomUUID(), bob.getId(), "Bob Smith", "INR", new BigDecimal("200.0000"));

        idempotencyKey = UUID.randomUUID();
    }

    @Test
    void executeTransfer_happyPath_shouldPerformDoubleEntryAndAdjustBalances() {
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("300.0000"),
                "INR"
        );

        TransferResponse response = transferService.executeTransfer(alice.getEmail(), idempotencyKey, request);

        // Verify Transfer
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.getAmount()).isEqualByComparingTo("300.0000");

        // Verify Balances: Source debited 300, Dest credited 300
        assertThat(sourceAccount.getBalance()).isEqualByComparingTo("700.0000");
        assertThat(destAccount.getBalance()).isEqualByComparingTo("500.0000");

        // Verify Double-Entry Ledger Entries (1 DEBIT, 1 CREDIT)
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());
        List<LedgerEntry> savedEntries = entryCaptor.getAllValues();

        assertThat(savedEntries).hasSize(2);
        assertThat(savedEntries.get(0).getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(savedEntries.get(0).getAmount()).isEqualByComparingTo("300.0000");
        assertThat(savedEntries.get(1).getDirection()).isEqualTo(Direction.CREDIT);
        assertThat(savedEntries.get(1).getAmount()).isEqualByComparingTo("300.0000");
    }

    @Test
    void executeTransfer_whenIdempotencyKeyAlreadyExists_shouldReturnCachedResultWithoutExecuting() {
        Transfer existing = new Transfer(
                UUID.randomUUID(),
                idempotencyKey,
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("100.0000"),
                "INR"
        );
        existing.markCompleted();

        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("100.0000"),
                "INR"
        );

        TransferResponse response = transferService.executeTransfer(alice.getEmail(), idempotencyKey, request);

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void executeTransfer_whenSelfTransfer_shouldThrowException() {
        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                sourceAccount.getId(),
                new BigDecimal("50.0000"),
                "INR"
        );

        assertThatThrownBy(() -> transferService.executeTransfer(alice.getEmail(), idempotencyKey, request))
                .isInstanceOf(SelfTransferNotAllowedException.class);
    }

    @Test
    void executeTransfer_whenInsufficientBalance_shouldThrowException() {
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("5000.0000"), // Alice only has 1000
                "INR"
        );

        assertThatThrownBy(() -> transferService.executeTransfer(alice.getEmail(), idempotencyKey, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void executeTransfer_whenCurrencyMismatch_shouldThrowException() {
        destAccount.setCurrency("USD");

        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("100.0000"),
                "INR"
        );

        assertThatThrownBy(() -> transferService.executeTransfer(alice.getEmail(), idempotencyKey, request))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void executeTransfer_whenSourceAccountFrozen_shouldThrowException() {
        sourceAccount.setFrozen(true);

        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("100.0000"),
                "INR"
        );

        assertThatThrownBy(() -> transferService.executeTransfer(alice.getEmail(), idempotencyKey, request))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("Source account is frozen");
    }

    @Test
    void executeTransfer_whenCallerDoesNotOwnSourceAccount_shouldThrowForbidden() {
        // Bob attempts to debit Alice's account
        when(transferRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));
        when(accountRepository.findByIdForUpdate(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByIdForUpdate(destAccount.getId())).thenReturn(Optional.of(destAccount));

        TransferRequest request = new TransferRequest(
                sourceAccount.getId(),
                destAccount.getId(),
                new BigDecimal("100.0000"),
                "INR"
        );

        assertThatThrownBy(() -> transferService.executeTransfer(bob.getEmail(), idempotencyKey, request))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("do not have permission to debit");
    }
}
