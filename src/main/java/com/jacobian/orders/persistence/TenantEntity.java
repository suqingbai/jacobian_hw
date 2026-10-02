package com.jacobian.orders.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Reference data maintained outside this service; read-only here. */
@Entity
@Immutable
@Table(name = "tenants")
public class TenantEntity {

  @Id private UUID id;
  private String name;
  private Instant createdAt;

  protected TenantEntity() {}

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
