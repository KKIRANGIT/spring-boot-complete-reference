package com.bankflow.account.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Gateway-authenticated principal for downstream account-service authorization checks.
 *
 * <p>Plain English: the gateway has already validated the JWT, so this principal only carries the
 * trusted user id and roles needed for ownership and admin decisions.
 */
@Getter
public class CustomUserDetails implements UserDetails, Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  private final UUID id;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomUserDetails(UUID id, Collection<? extends GrantedAuthority> authorities) {
    this.id = id;
    this.authorities = authorities;
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return id.toString();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
