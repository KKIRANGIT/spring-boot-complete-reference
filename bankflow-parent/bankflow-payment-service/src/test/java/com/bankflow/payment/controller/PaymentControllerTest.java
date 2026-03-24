package com.bankflow.payment.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bankflow.common.domain.SagaStatus;
import com.bankflow.common.domain.TransactionStatus;
import com.bankflow.common.domain.TransactionType;
import com.bankflow.common.handler.GlobalExceptionHandler;
import com.bankflow.payment.dto.TransactionResponse;
import com.bankflow.payment.dto.TransferRequest;
import com.bankflow.payment.dto.TransferResponse;
import com.bankflow.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MVC tests for {@link PaymentController}.
 *
 * <p>Plain English: these tests prove the HTTP contract and BankFlow response wrapper stay stable
 * for callers of the payment service without needing a full Spring Boot context.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

  @Mock
  private PaymentService paymentService;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().findAndRegisterModules();
    mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("Posting a transfer should return 202 Accepted and the BankFlow API wrapper")
  void initiateTransfer_shouldReturnAccepted() throws Exception {
    // Arrange
    UUID transactionId = UUID.randomUUID();
    TransferRequest request = new TransferRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("125.00"),
        "INR",
        "Controller test");
    TransferResponse response = new TransferResponse(
        transactionId,
        "TXN17100000000000001",
        TransactionStatus.PENDING,
        SagaStatus.STARTED,
        new BigDecimal("125.00"),
        "INR",
        LocalDateTime.now());

    when(paymentService.initiateTransfer(eq(request), eq("idem-controller"))).thenReturn(response);

    // Act + Assert
    // This catches contract regressions where the controller returns the wrong HTTP status or wraps
    // the payload differently than the frontend expects.
    mockMvc.perform(post("/api/v1/payments/transfers")
            .header("Idempotency-Key", "idem-controller")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.transactionReference").value("TXN17100000000000001"));
  }

  @Test
  @DisplayName("Getting a transaction by id should return the stored transaction details")
  void getTransaction_shouldReturnOk() throws Exception {
    // Arrange
    UUID transactionId = UUID.randomUUID();
    TransactionResponse response = new TransactionResponse(
        transactionId,
        "TXN17100000000000002",
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("200.00"),
        "INR",
        TransactionType.TRANSFER,
        TransactionStatus.PROCESSING,
        SagaStatus.ACCOUNT_DEBITED,
        "Lookup test",
        null,
        LocalDateTime.now().minusMinutes(1),
        null);
    when(paymentService.getTransaction(transactionId)).thenReturn(response);

    // Act + Assert
    mockMvc.perform(get("/api/v1/payments/{transactionId}", transactionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionReference").value("TXN17100000000000002"));
  }

  @Test
  @DisplayName("Getting a transaction by reference should return the stored transaction details")
  void getTransactionByReference_shouldReturnOk() throws Exception {
    // Arrange
    TransactionResponse response = new TransactionResponse(
        UUID.randomUUID(),
        "TXN17100000000000003",
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BigDecimal("300.00"),
        "INR",
        TransactionType.TRANSFER,
        TransactionStatus.COMPLETED,
        SagaStatus.COMPLETED,
        "Reference lookup",
        null,
        LocalDateTime.now().minusMinutes(2),
        LocalDateTime.now().minusMinutes(1));
    when(paymentService.getTransactionByReference("TXN17100000000000003")).thenReturn(response);

    // Act + Assert
    mockMvc.perform(get("/api/v1/payments/reference/{transactionReference}", "TXN17100000000000003"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"));
  }
}
