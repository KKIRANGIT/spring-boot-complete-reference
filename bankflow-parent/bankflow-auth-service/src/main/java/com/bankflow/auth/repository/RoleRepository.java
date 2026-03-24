package com.bankflow.auth.repository;

import com.bankflow.auth.entity.Role;
import com.bankflow.auth.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for seeded roles.
 *
 * <p>Plain English: this interface finds predefined roles such as {@code ROLE_USER}.
 *
 * <p>Interview question answered: "How do you keep registration logic tied to seeded RBAC roles?"
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

  /**
   * Finds a role by its enum name.
   */
  Optional<Role> findByName(RoleName name);
}
