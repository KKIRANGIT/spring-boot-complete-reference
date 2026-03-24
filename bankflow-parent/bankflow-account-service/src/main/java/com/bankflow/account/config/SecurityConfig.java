package com.bankflow.account.config;

import com.bankflow.account.security.GatewayAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Security configuration for the internal account-service.
 *
 * <p>Plain English: the gateway authenticates internet traffic, while this service trusts internal
 * identity headers and uses method security for authorization.
 *
 * <p>Interview question answered: "How do downstream microservices trust a gateway instead of
 * re-validating JWTs on every hop?"
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

  public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
    this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
  }

  /**
   * Declares a stateless filter chain that accepts gateway identity headers.
   */
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
