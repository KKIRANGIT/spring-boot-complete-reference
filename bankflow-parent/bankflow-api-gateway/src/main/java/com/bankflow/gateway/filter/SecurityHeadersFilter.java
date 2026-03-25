package com.bankflow.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds hardened browser-response headers to every gateway response.
 *
 * <p>Plain English: even though BankFlow is API-first, browsers still consume some responses during
 * local development, so these headers prevent clickjacking, MIME sniffing, and stale cache replay.
 */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

  @Override
  public int getOrder() {
    return -50;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    HttpHeaders headers = exchange.getResponse().getHeaders();
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("X-Frame-Options", "DENY");
    headers.setCacheControl("no-cache, no-store, must-revalidate");
    headers.setPragma("no-cache");
    return chain.filter(exchange);
  }
}
