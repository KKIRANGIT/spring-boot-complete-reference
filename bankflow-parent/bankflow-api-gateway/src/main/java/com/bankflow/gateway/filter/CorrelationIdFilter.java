package com.bankflow.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds a correlation id to every request before any other gateway logic runs.
 *
 * <p>Plain English: this lets developers and operators trace one request across auth, account,
 * payment, and notification logs using the same id.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  public int getOrder() {
    return -200;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startTime = System.currentTimeMillis();
    String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    ServerHttpRequest request = exchange.getRequest().mutate()
        .header("X-Correlation-Id", correlationId)
        .build();
    ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();

    String finalCorrelationId = correlationId;
    return chain.filter(mutatedExchange)
        .doFinally(signalType -> log.info(
            "{} {} {} {}ms",
            finalCorrelationId,
            request.getMethod(),
            request.getPath().value(),
            System.currentTimeMillis() - startTime));
  }
}
