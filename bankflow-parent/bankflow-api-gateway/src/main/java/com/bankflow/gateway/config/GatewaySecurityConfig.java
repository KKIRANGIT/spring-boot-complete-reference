package com.bankflow.gateway.config;

import java.util.function.Consumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;

/**
 * Reactive gateway security configuration.
 *
 * <p>Plain English: the gateway still relies on custom filters for JWT validation, but Spring
 * Security is used to enforce hardened response headers and disable browser-oriented features.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .headers(headers -> headers
            .frameOptions(frame -> frame.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
            .contentTypeOptions(Customizer.withDefaults())
            .cache(cache -> cache.disable()));

    return http.build();
  }
}
