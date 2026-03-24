package com.bankflow.auth.security;

import com.bankflow.auth.entity.User;
import com.bankflow.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads auth users for Spring Security.
 *
 * <p>Plain English: this service converts usernames from incoming JWTs into authenticated
 * principals by fetching the matching user and roles from MySQL.
 *
 * <p>Design decision: roles are loaded with the user query so security evaluation never depends on
 * a lazy collection outside the repository transaction.
 *
 * <p>Interview question answered: "How does Spring Security find a user during authentication?"
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  /** Loads a user by username for JWT authentication. */
  @Override
  public UserDetails loadUserByUsername(String username) {
    User user = userRepository.findByUsernameWithRoles(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return CustomUserDetails.fromUser(user);
  }
}
