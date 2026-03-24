package com.bankflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.domain.TransactionType;
import com.bankflow.common.event.PaymentInitiatedEvent;
import com.bankflow.payment.config.OpenApiConfig;
import com.bankflow.payment.dto.TransactionResponse;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.entity.OutboxEvent;
import com.bankflow.payment.entity.Transaction;
import com.bankflow.payment.security.GatewayAuthenticationFilter;
import com.bankflow.payment.service.OutboxEventFactory;
import com.bankflow.payment.service.PaymentMapper;
import com.bankflow.payment.service.TransactionReferenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Focused coverage tests for helper classes that support payment-service behavior.
 *
 * <p>Plain English: these tests cover mappers, security header parsing, OpenAPI metadata, outbox
 * serialization, and the main entry point without needing a Spring context.
 */
class PaymentSupportTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Payment mapper should convert the transaction aggregate into transfer and transaction response DTOs")
  void paymentMapper_shouldMapTransferAndTransactionResponses() {
    // Arrange
    PaymentMapper paymentMapper = new PaymentMapper();
    Transaction transaction = sampleTransaction();

    // Act
    TransferResponse transferResponse = paymentMapper.toTransferResponse(transaction);
    TransactionResponse transactionResponse = paymentMapper.toTransactionResponse(transaction);

    // Assert
    assertThat(transferResponse.transactionReference()).isEqualTo("TXN17100000000001234");
    assertThat(transactionResponse.type()).isEqualTo(TransactionType.TRANSFER);
    assertThat(transactionResponse.failureReason()).isEqualTo("NONE");
  }

  @Test
  @DisplayName("Outbox event factory should serialize a typed payment event into a transaction outbox row")
  void outboxEventFactory_shouldSerializePayloadIntoOutboxRow() {
    // Arrange
    OutboxEventFactory outboxEventFactory =
        new OutboxEventFactory(new ObjectMapper().registerModule(new JavaTimeModule()));
    UUID aggregateId = UUID.randomUUID();

    // Act
    OutboxEvent outboxEvent = outboxEventFactory.createTransactionOutboxEvent(
        aggregateId,
        com.bankflow.common.kafka.KafkaTopics.PAYMENT_INITIATED,
        new PaymentInitiatedEvent(
            UUID.randomUUID(),
            aggregateId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("75.00"),
            "INR",
            LocalDateTime.now()));

    // Assert
    assertThat(outboxEvent.getAggregateId()).isEqualTo(aggregateId.toString());
    assertThat(outboxEvent.getEventType()).isEqualTo(com.bankflow.common.kafka.KafkaTopics.PAYMENT_INITIATED);
    assertThat(outboxEvent.getPayload()).contains("\"transactionId\"");
  }

  @Test
  @DisplayName("Transaction reference generator should return a TXN-prefixed identifier with a four-digit random suffix")
  void transactionReferenceGenerator_shouldGenerateReadableReference() {
    // Arrange
    TransactionReferenceGenerator transactionReferenceGenerator = new TransactionReferenceGenerator();

    // Act
    String reference = transactionReferenceGenerator.generate();

    // Assert
    assertThat(reference).matches("TXN\\d{17}");
  }

  @Test
  @DisplayName("Gateway authentication filter should install user id and normalized roles from trusted headers")
  void gatewayAuthenticationFilter_withHeaders_shouldPopulateSecurityContext() throws Exception {
    // Arrange
    GatewayAuthenticationFilter gatewayAuthenticationFilter = new GatewayAuthenticationFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    request.addHeader("X-User-Roles", "ADMIN,USER");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    gatewayAuthenticationFilter.doFilter(request, response, new MockFilterChain());

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
  }

  @Test
  @DisplayName("Gateway authentication filter should ignore malformed UUID headers instead of crashing the request")
  void gatewayAuthenticationFilter_withMalformedHeader_shouldIgnoreAuthentication() throws Exception {
    // Arrange
    GatewayAuthenticationFilter gatewayAuthenticationFilter = new GatewayAuthenticationFilter();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-User-Id", "not-a-uuid");
    MockHttpServletResponse response = new MockHttpServletResponse();

    // Act
    gatewayAuthenticationFilter.doFilter(request, response, new MockFilterChain());

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("OpenAPI config should expose the payment service title and gateway header security scheme")
  void openApiConfig_shouldExposeGatewayHeaderScheme() {
    // Arrange
    OpenApiConfig openApiConfig = new OpenApiConfig();

    // Act
    OpenAPI openAPI = openApiConfig.paymentServiceOpenApi();

    // Assert
    assertThat(openAPI.getInfo().getTitle()).isEqualTo("BankFlow Payment Service API");
    assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("gatewayHeaders");
    assertThat(openAPI.getSecurity()).hasSize(1);
  }

  @Test
  @DisplayName("Main method should delegate to SpringApplication.run with the payment service class")
  void main_shouldDelegateToSpringApplication() {
    // Arrange
    String[] args = {"--spring.profiles.active=test"};

    // Act + Assert
    try (var springApplication = mockStatic(SpringApplication.class)) {
      BankflowPaymentServiceApplication.main(args);
      springApplication.verify(() -> SpringApplication.run(BankflowPaymentServiceApplication.class, args));
    }
  }

  private Transaction sampleTransaction() {
    Transaction transaction = new Transaction();
    transaction.setId(UUID.randomUUID());
    transaction.setTransactionReference("TXN17100000000001234");
    transaction.setIdempotencyKey("idem-support");
    transaction.setFromAccountId(UUID.randomUUID());
    transaction.setToAccountId(UUID.randomUUID());
    transaction.setAmount(new BigDecimal("500.00"));
    transaction.setCurrency("INR");
    transaction.setType(TransactionType.TRANSFER);
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setSagaStatus(SagaStatus.COMPLETED);
    transaction.setDescription("Mapped transfer");
    transaction.setFailureReason("NONE");
    transaction.setCreatedAt(LocalDateTime.now().minusMinutes(5));
    transaction.setCompletedAt(LocalDateTime.now().minusMinutes(1));
    return transaction;
  }
}
