package com.bankflow.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.dto.AccountBalanceResponse;
import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.AccountStatementEntryResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.dto.UpdateAccountStatusRequest;
import com.bankflow.account.service.AccountCommandService;
import com.bankflow.account.service.AccountQueryService;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link AccountController}.
 *
 * <p>Plain English: these tests prove the HTTP layer delegates to the CQRS services and wraps every
 * response in the shared {@code ApiResponse} contract.
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

  private static final UUID ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID USER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

  @Mock
  private AccountCommandService accountCommandService;

  @Mock
  private AccountQueryService accountQueryService;

  @InjectMocks
  private AccountController accountController;

  @Test
  @DisplayName("Create account should return HTTP 201 with the created account payload")
  void createAccount_shouldReturnCreatedResponse() {
    // Arrange
    AccountResponse accountResponse = accountResponse();
    when(accountCommandService.createAccount(org.mockito.ArgumentMatchers.any())).thenReturn(accountResponse);

    // Act
    var response = accountController.createAccount(
        USER_ID,
        new CreateAccountRequest(AccountType.SAVINGS, new BigDecimal("5000.00"), "INR"));

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getData()).isEqualTo(accountResponse);
    verify(accountCommandService).createAccount(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("Get account by id should return the account wrapped in the shared success envelope")
  void getAccountById_shouldReturnOkResponse() {
    // Arrange
    when(accountQueryService.getAccountById(ACCOUNT_ID)).thenReturn(accountResponse());

    // Act
    var response = accountController.getAccountById(ACCOUNT_ID);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().id()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @DisplayName("Get my accounts should return every account owned by the gateway-authenticated user")
  void getMyAccounts_shouldReturnOwnerAccounts() {
    // Arrange
    when(accountQueryService.getAccountsByUserId(USER_ID)).thenReturn(List.of(accountResponse()));

    // Act
    var response = accountController.getMyAccounts(USER_ID);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData()).hasSize(1);
  }

  @Test
  @DisplayName("Get balance should return the lightweight balance payload")
  void getBalance_shouldReturnBalanceResponse() {
    // Arrange
    when(accountQueryService.getBalance(ACCOUNT_ID)).thenReturn(new AccountBalanceResponse(
        ACCOUNT_ID,
        new BigDecimal("1500.00"),
        "INR",
        AccountStatus.ACTIVE,
        LocalDateTime.now()));

    // Act
    var response = accountController.getBalance(ACCOUNT_ID);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().balance()).isEqualByComparingTo("1500.00");
  }

  @Test
  @DisplayName("Get statement should return the paged audit trail response")
  void getStatement_shouldReturnPageResponse() {
    // Arrange
    Page<AccountStatementEntryResponse> statement = new PageImpl<>(List.of(new AccountStatementEntryResponse(
        UUID.randomUUID(),
        "DEBITED",
        new BigDecimal("1000.00"),
        new BigDecimal("700.00"),
        new BigDecimal("300.00"),
        USER_ID,
        LocalDateTime.now().minusMinutes(3),
        "ATM withdrawal")));
    when(accountQueryService.getAccountStatement(ACCOUNT_ID, org.springframework.data.domain.PageRequest.of(0, 20)))
        .thenReturn(statement);

    // Act
    var response = accountController.getStatement(ACCOUNT_ID, 0, 20);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().getContent()).hasSize(1);
  }

  @Test
  @DisplayName("Update status should return the updated account document after the command succeeds")
  void updateStatus_shouldReturnUpdatedAccount() {
    // Arrange
    AccountResponse closedAccount = new AccountResponse(
        ACCOUNT_ID,
        "BNK123456789",
        USER_ID,
        AccountType.SAVINGS,
        new BigDecimal("1500.00"),
        "INR",
        AccountStatus.CLOSED,
        LocalDateTime.now().minusDays(1),
        LocalDateTime.now());
    when(accountCommandService.updateAccountStatus(org.mockito.ArgumentMatchers.any())).thenReturn(closedAccount);

    // Act
    var response = accountController.updateStatus(
        ACCOUNT_ID,
        USER_ID,
        new UpdateAccountStatusRequest(AccountStatus.CLOSED, "Customer requested closure"));

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getData().status()).isEqualTo(AccountStatus.CLOSED);
  }

  private AccountResponse accountResponse() {
    return new AccountResponse(
        ACCOUNT_ID,
        "BNK123456789",
        USER_ID,
        AccountType.SAVINGS,
        new BigDecimal("1500.00"),
        "INR",
        AccountStatus.ACTIVE,
        LocalDateTime.now().minusDays(3),
        LocalDateTime.now().minusMinutes(1));
  }
}
