package com.bankflow.auth.repository;

import com.bankflow.auth.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for reading and writing auth users.
 *
 * <p>Plain English: this interface is how the auth service looks users up by username, email, or
 * id while keeping role data ready for security decisions.
 *
 * <p>Interview question answered: "How do you fetch a user with roles efficiently for login and
 * JWT authentication?"
 */
public interface UserRepository extends JpaRepository<User, UUID> {

  /**
   * Checks whether an email is already taken.
   */
  boolean existsByEmailIgnoreCase(String email);

  /**
   * Checks whether a username is already taken.
   */
  boolean existsByUsernameIgnoreCase(String username);

  /**
   * Loads a user by username and eagerly fetches roles for authentication.
   */
  @Query("select distinct u from User u left join fetch u.roles where lower(u.username) = lower(:username)")
  Optional<User> findByUsernameWithRoles(@Param("username") String username);

  /**
   * Loads a user by username or email and eagerly fetches roles for login.
   */
  @Query("""
      select distinct u
      from User u
      left join fetch u.roles
      where lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier)
      """)
  Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

  /**
   * Loads a user by id together with roles for profile and logout flows.
   */
  @Query("select distinct u from User u left join fetch u.roles where u.id = :id")
  Optional<User> findByIdWithRoles(@Param("id") UUID id);
}
