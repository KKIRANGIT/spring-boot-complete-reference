package com.bankflow.gateway.config;

import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Gateway support configuration.
 *
 * <p>Plain English: this provides the rate-limiter key strategy used by Spring Cloud Gateway to
 * throttle authenticated users by user id and anonymous callers by client IP.
 */
@Configuration
public class GatewayConfig {

  @Bean
  public KeyResolver userKeyResolver() {
    return exchange -> {
      String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
      if (userId != null && !userId.isBlank()) {
        return Mono.just(userId);
      }

      InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
      if (remoteAddress != null && remoteAddress.getAddress() != null) {
        return Mono.just(remoteAddress.getAddress().getHostAddress());
      }

      return Mono.just("unknown");
    };
  }
}
