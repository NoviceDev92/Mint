package com.mint.account;

import com.mint.account.dto.AccountResponse;
import com.mint.account.dto.CreateAccountRequest;
import com.mint.auth.User;
import com.mint.auth.UserRepository;
import com.mint.common.exception.UnauthorizedAccountAccessException;
import com.mint.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private AccountService accountService;

    private User alice;
    private User bob;
    private Account aliceAccount;

    @BeforeEach
    void setUp() {
        alice = new User(UUID.randomUUID(), "alice@mint.internal", "hashed", "Alice Doe", "ROLE_USER");
        bob = new User(UUID.randomUUID(), "bob@mint.internal", "hashed", "Bob Smith", "ROLE_USER");

        aliceAccount = new Account(UUID.randomUUID(), alice.getId(), "Alice Doe", "INR", new BigDecimal("1000.0000"));
    }

    @Test
    void createAccount_shouldSaveAndReturnAccountResponse() {
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAccountRequest request = new CreateAccountRequest("INR", new BigDecimal("500.0000"));
        AccountResponse response = accountService.createAccount(alice.getEmail(), request);

        assertThat(response).isNotNull();
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getBalance()).isEqualByComparingTo("500.0000");
        assertThat(response.getOwnerName()).isEqualTo("Alice Doe");
    }

    @Test
    void getAccountById_whenOwnerRequests_shouldReturnAccount() {
        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        AccountResponse response = accountService.getAccountById(aliceAccount.getId(), alice.getEmail());

        assertThat(response.getId()).isEqualTo(aliceAccount.getId());
        assertThat(response.getBalance()).isEqualByComparingTo("1000.0000");
    }

    @Test
    void getAccountById_whenNonOwnerRequests_shouldThrowUnauthorized() {
        when(accountRepository.findById(aliceAccount.getId())).thenReturn(Optional.of(aliceAccount));
        when(userRepository.findByEmail(bob.getEmail())).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> accountService.getAccountById(aliceAccount.getId(), bob.getEmail()))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("do not have permission");
    }
}
