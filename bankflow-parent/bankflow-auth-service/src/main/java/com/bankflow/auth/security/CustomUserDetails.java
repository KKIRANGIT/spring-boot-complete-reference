package com.bankflow.auth.security;

import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.User;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Security principal used inside Spring Security for authenticated users.
 *
 * <p>Plain English: this is the in-memory authenticated view of a {@link User} record.
 *
 * <p>Design decision: the principal carries user id, email, and roles so downstream services do
 * not need extra database lookups just to build JWTs or resolve the current user.
 *
 * <p>Bug prevented: relying on username alone makes it harder to populate claims and increases the
 * chance of mismatched identity data during token generation.
 *
 * <p>Interview question answered: "How do you adapt a JPA user entity into Spring Security's
 * UserDetails model?"
 */
public class CustomUserDetails implements UserDetails {

  private final UUID id;
  private final String username;
  private final String email;
  private final String password;
  private final boolean active;
  private final LocalDateTime lockedUntil;
  private final List<GrantedAuthority> authorities;

  private CustomUserDetails(
      UUID id,
      String username,
      String email,
      String password,
      boolean active,
      LocalDateTime lockedUntil,
      List<GrantedAuthority> authorities) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.password = password;
    this.active = active;
    this.lockedUntil = lockedUntil;
    this.authorities = authorities;
  }

  /**
   * Creates a security principal from a persisted auth user.
   *
   * <p>Plain English: this copies the entity into the lightweight object Spring Security expects.
   *
   * <p>Interview question answered: "Where do you translate database users into authentication
   * principals?"
   */
  public static CustomUserDetails fromUser(User user) {
    List<GrantedAuthority> authorities = user.getRoles().stream()
        .map(Role::getName)
        .map(Enum::name)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());

    return new CustomUserDetails(
        user.getId(),
        user.getUsername(),
        user.getEmail(),
        user.getPassword(),
        user.isActive(),
        user.getLockedUntil(),
        authorities);
  }

  /** Returns the database user id for claim building and logout flows. */
  public UUID getId() {
    return id;
  }

  /** Returns the user's email address for JWT custom claims. */
  public String getEmail() {
    return email;
  }

  /** Returns role names as strings for API responses and JWT claims. */
  public List<String> getRoleNames() {
    return authorities.stream().map(GrantedAuthority::getAuthority).toList();
  }

  /** Returns the Spring Security authorities derived from roles. */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }

  /** Returns the BCrypt password hash used for credential matching. */
  @Override
  public String getPassword() {
    return password;
  }

  /** Returns the canonical username used as the JWT subject. */
  @Override
  public String getUsername() {
    return username;
  }

  /** Auth accounts never expire in this baseline implementation. */
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  /** Reports whether the account lockout window is currently inactive. */
  @Override
  public boolean isAccountNonLocked() {
    return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
  }

  /** Credentials do not expire in this baseline implementation. */
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /** Reports whether the user is active. */
  @Override
  public boolean isEnabled() {
    return active;
  }
}
