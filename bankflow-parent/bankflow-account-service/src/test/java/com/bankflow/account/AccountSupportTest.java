package com.bankflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankflow.account.config.OpenApiConfig;
import com.bankflow.account.entity.Account;
import com.bankflow.account.entity.AccountAuditLog;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.security.AccountSecurity;
import com.bankflow.account.security.GatewayAuthenticationFilter;
import com.bankflow.account.service.AccountNumberGenerator;
import com.bankflow.account.service.AccountViewMapper;
import com.bankflow.common.domain.AccountStatus;
import com.bankflow.common.domain.AccountType;
import io.swagger.v3.oas.models.OpenAPI;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Focused coverage tests for account-service support classes.
 *
 * <p>Plain English: this suite covers the helper classes that support controller and service logic
 * but do not need a database or Spring Boot context.
 */
@ExtendWith(MockitoExtension.class)
class AccountSupportTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Account number generator should return a BNK-prefixed 9-digit number and check uniqueness")
  void accountNumberGenerator_shouldGeneratePrefixedUniqueNumber() {
    // Arrange
    AccountRepository accountRepository = mock(AccountRepository.class);
    AccountNumberGenerator generator = new AccountNumberGenerator(accountRepository);
    when(accountRepository.existsByAccountNumber(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

    // Act
    String accountNumber = generator.generate();

    // Assert
    assertThat(accountNumber).matches("BNK\\d{9}");
    verify(accountRepository).existsByAccountNumber(accountNumber);
  }

  @Test
  @DisplayName("Account view mapper should shape account and audit entities into API responses")
  void accountViewMapper_shouldMapAccountAndStatement() {
    // Arrange
    AccountViewMapper mapper = new AccountViewMapper();
    Account account = new Account();
    account.setId(UUID.randomUUID());
    account.setAccountNumber("BNK123456789");
    account.setUserId(UUID.randomUUID());
    account.setAccountType(AccountType.SAVINGS);
    account.setBalance(new BigDecimal("1000.00"));
    account.setCurrency("INR");
    account.setStatus(AccountStatus.ACTIVE);
    account.setCreatedAt(LocalDateTime.now().minusDays(1));
    account.setUpdatedAt(LocalDateTime.now());

    AccountAuditLog auditLog = new AccountAuditLog();
    auditLog.setId(UUID.randomUUID());
    auditLog.setAction("DEBITED");
    auditLog.setPreviousBalance(new BigDecimal("1000.00"));
    auditLog.setNewBalance(new BigDecimal("700.00"));
    auditLog.setAmount(new BigDecimal("300.00"));
    auditLog.setPerformedAt(LocalDateTime.now());
    auditLog.setDescription("ATM withdrawal");

    // Act
    var accountResponse = mapper.toAccountResponse(account);
    var balanceResponse = mapper.toBalanceResponse(account);
    var statementEntry = mapper.toStatementEntry(auditLog);

    // Assert
    assertThat(accountResponse.accountNumber()).isEqualTo("BNK123456789");
    assertThat(balanceResponse.balance()).isEqualByComparingTo("1000.00");
    assertThat(statementEntry.action()).isEqualTo("DEBITED");
  }

  @Test
  @DisplayName("Account security should return true only when the authenticated user owns the account")
  void accountSecurity_shouldRecognizeOwnership() {
    // Arrange
    UUID accountId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    AccountRepository accountRepository = mock(AccountRepository.class);
    AccountSecurity accountSecurity = new AccountSecurity(accountRepository);
    when(accountRepository.findUserIdById(accountId)).thenReturn(Optional.of(ownerId));
    var authentication = new UsernamePasswordAuthenticationToken(ownerId.toString(), null, java.util.List.of());

    // Act
    boolean owner = accountSecurity.isOwner(authentication, accountId);

    // Assert
    assertThat(owner).isTrue();
  }

  @Test
  @DisplayName("Gateway authentication filter should install user id and roles from trusted headers")
  void gatewayAuthenticationFilter_withHeaders_shouldPopulateSecurityContext() throws Exception {
    // Arrange
    GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    request.addHeader("X-User-Roles", "ADMIN,TELLER");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // Act
    filter.doFilter(request, response, chain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_TELLER");
  }

  @Test
  @DisplayName("Gateway authentication filter should ignore malformed user ids instead of crashing the request")
  void gatewayAuthenticationFilter_withMalformedHeader_shouldIgnoreAuthentication() throws Exception {
    // Arrange
    GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "not-a-uuid");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    // Act
    filter.doFilter(request, response, chain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("OpenAPI config should publish the account service title and gateway header security scheme")
  void openApiConfig_shouldExposeGatewayHeaderScheme() {
    // Arrange
    OpenApiConfig openApiConfig = new OpenApiConfig();

    // Act
    OpenAPI openAPI = openApiConfig.accountServiceOpenApi();

    // Assert
    assertThat(openAPI.getInfo().getTitle()).isEqualTo("BankFlow Account Service API");
    assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("gatewayHeaders");
    assertThat(openAPI.getSecurity()).hasSize(1);
  }

  @Test
  @DisplayName("Main method should delegate to SpringApplication.run with the account service class")
  void main_shouldDelegateToSpringApplication() {
    // Arrange
    String[] args = {"--spring.profiles.active=test"};

    // Act + Assert
    try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
      BankflowAccountServiceApplication.main(args);
      springApplication.verify(() -> SpringApplication.run(BankflowAccountServiceApplication.class, args));
    }
  }
}
