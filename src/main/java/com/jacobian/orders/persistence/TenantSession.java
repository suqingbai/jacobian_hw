package com.jacobian.orders.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Binds the current transaction to a tenant for row-level security ({@code V3__tenant_rls.sql}).
 *
 * <p>{@code set_config(..., true)} is transaction-local, so it never leaks to the next user of a
 * pooled connection, and an unbound transaction sees no tenant rows at all.
 */
@Component
public class TenantSession {

  private final JdbcTemplate jdbc;

  public TenantSession(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void bind(UUID tenantId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Tenant binding needs an active transaction");
    }
    jdbc.queryForObject(
        "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
  }
}
