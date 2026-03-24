package com.bankflow.payment.config;

import com.bankflow.payment.security.GatewayAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Security configuration for the internal payment-service.
 *
 * <p>Plain English: the gateway authenticates internet traffic, while payment-service trusts the
 * identity headers stamped by that gateway for internal authorization decisions.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

  public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
    this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(form -> form.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/prometheus",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html")
            .permitAll()
            .anyRequest()
            .authenticated())
        .addFilterBefore(gatewayAuthenticationFilter, AnonymousAuthenticationFilter.class);

    return http.build();
  }
}
