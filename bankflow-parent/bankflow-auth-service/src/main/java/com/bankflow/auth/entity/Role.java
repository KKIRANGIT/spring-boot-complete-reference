package com.bankflow.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Role entity stored in the {@code roles} table.
 *
 * <p>Plain English: this table holds the fixed security roles that users can be granted.
 *
 * <p>Design decision: roles are seeded by SQL on startup instead of being created in application
 * code so authorization metadata is deterministic and reproducible across environments.
 *
 * <p>Bug prevented: creating roles dynamically in Java can drift between nodes or environments and
 * accidentally grant inconsistent privileges.
 *
 * <p>Interview question answered: "How do you seed and model RBAC roles in a Spring Boot system?"
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "users")
@Entity
@Table(name = "roles")
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, unique = true, length = 30)
  private RoleName name;

  @ManyToMany(mappedBy = "roles")
  private Set<User> users = new HashSet<>();
}
