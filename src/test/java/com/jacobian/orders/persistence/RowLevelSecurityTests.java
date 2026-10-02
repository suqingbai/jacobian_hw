package com.jacobian.orders.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jacobian.orders.PostgresTestConfiguration;
import com.jacobian.orders.domain.OrderSubmission;
import com.jacobian.orders.domain.OrderType;
import com.jacobian.orders.domain.Priority;
import com.jacobian.orders.service.OrderService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tenant isolation enforced by Postgres itself, through the app's own connection ({@code
 * orders_app}): queries that forget the tenant filter still see only the bound tenant.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class RowLevelSecurityTests {

  private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private OrderService orders;
  @Autowired private TenantSession tenantSession;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;

  @Test
  void anUnfilteredQuerySeesOnlyTheBoundTenantsRows() {
    UUID id = createOrderFor(TENANT_A);
    String unfiltered = "SELECT count(*) FROM order_service.orders WHERE id = ?";
    String unfilteredItems = "SELECT count(*) FROM order_service.order_items WHERE order_id = ?";

    assertThat(countAs(TENANT_A, unfiltered, id)).isEqualTo(1);
    assertThat(countAs(TENANT_A, unfilteredItems, id)).isEqualTo(1);
    assertThat(countAs(TENANT_B, unfiltered, id)).isZero();
    assertThat(countAs(TENANT_B, unfilteredItems, id)).isZero();
    Long unbound = tx.execute(s -> jdbc.queryForObject(unfiltered, Long.class, id));
    assertThat(unbound).isZero();
  }

  @Test
  void writingAnotherTenantsRowIsRefused() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantSession.bind(TENANT_B);
                      jdbc.update(
                          """
                          INSERT INTO order_service.orders (tenant_id, external_order_id,
                              request_hash, patient_id, order_type, priority,
                              submitted_by_user_id, submitted_at)
                          VALUES (?, 'PO-SPOOF', ?, 'P-345678', 'L', 'R', 'u', now())
                          """,
                          TENANT_A,
                          new byte[32]);
                    }))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("row-level security");
  }

  @Test
  void theAppCannotWriteReferenceData() {
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s -> {
                      tenantSession.bind(TENANT_A);
                      jdbc.update(
                          "INSERT INTO order_service.patients VALUES (?, 'P-NEW', 'New', 'Person')",
                          TENANT_A);
                    }))
        .isInstanceOf(DataAccessException.class)
        .rootCause()
        .hasMessageContaining("permission denied");
  }

  @Test
  void bindingATenantNeedsATransaction() {
    assertThatThrownBy(() -> tenantSession.bind(TENANT_A))
        .isInstanceOf(IllegalStateException.class);
  }

  private UUID createOrderFor(UUID tenantId) {
    OrderSubmission submission =
        new OrderSubmission(
            tenantId,
            "PO-" + UUID.randomUUID(),
            "u-001",
            null,
            "P-345678",
            OrderType.IMAGING,
            Priority.URGENT,
            List.of(new OrderSubmission.Item("XR-CHEST", null, 1)),
            null,
            Instant.parse("2026-01-01T00:00:00Z"));
    return orders.submit(submission).order().id();
  }

  private long countAs(UUID tenantId, String sql, UUID id) {
    return tx.execute(
        s -> {
          tenantSession.bind(tenantId);
          return jdbc.queryForObject(sql, Long.class, id);
        });
  }
}
