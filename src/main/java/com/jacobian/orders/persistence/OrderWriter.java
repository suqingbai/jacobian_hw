package com.jacobian.orders.persistence;

import com.jacobian.orders.domain.OrderSubmission;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The idempotent write path (ADR-002). Runs inside the caller's transaction, after {@link
 * TenantSession#bind}, so row-level security checks every row it writes.
 */
@Repository
public class OrderWriter {

  private static final String INSERT_ORDER =
      """
      INSERT INTO order_service.orders (
          tenant_id, external_order_id, request_hash, patient_id, order_type, priority,
          submitted_by_user_id, submitted_by_display_name, notes, submitted_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (tenant_id, external_order_id) DO NOTHING
      RETURNING id
      """;

  private static final String INSERT_ITEM =
      """
      INSERT INTO order_service.order_items (
          tenant_id, order_id, line_no, code, description, quantity)
      VALUES (?, ?, ?, ?, ?, ?)
      """;

  private static final String FIND_BY_EXTERNAL_ID =
      """
      SELECT id, request_hash FROM order_service.orders
      WHERE tenant_id = ? AND external_order_id = ?
      """;

  private final JdbcTemplate jdbc;

  public OrderWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Inserts the order unless {@code (tenant_id, external_order_id)} already exists. Returns the new
   * id, or empty for a duplicate. A concurrent identical insert makes this wait for the other
   * transaction and then return empty.
   */
  public Optional<UUID> insertOrder(OrderSubmission order, byte[] requestHash) {
    List<UUID> ids =
        jdbc.query(
            INSERT_ORDER,
            (rs, row) -> rs.getObject(1, UUID.class),
            order.tenantId(),
            order.externalOrderId(),
            requestHash,
            order.patientId(),
            String.valueOf(order.orderType().code()),
            String.valueOf(order.priority().code()),
            order.submittedByUserId(),
            order.submittedByDisplayName(),
            order.notes(),
            OffsetDateTime.ofInstant(order.submittedAt(), ZoneOffset.UTC));
    return ids.stream().findFirst();
  }

  /** Inserts the items with 1-based line numbers in submitted order. */
  public void insertItems(UUID tenantId, UUID orderId, List<OrderSubmission.Item> items) {
    jdbc.batchUpdate(
        INSERT_ITEM,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            OrderSubmission.Item item = items.get(i);
            ps.setObject(1, tenantId);
            ps.setObject(2, orderId);
            ps.setInt(3, i + 1);
            ps.setString(4, item.code());
            ps.setString(5, item.description());
            ps.setInt(6, item.quantity());
          }

          @Override
          public int getBatchSize() {
            return items.size();
          }
        });
  }

  public Optional<ExistingOrder> findByExternalId(UUID tenantId, String externalOrderId) {
    return jdbc
        .query(
            FIND_BY_EXTERNAL_ID,
            (rs, row) -> new ExistingOrder(rs.getObject(1, UUID.class), rs.getBytes(2)),
            tenantId,
            externalOrderId)
        .stream()
        .findFirst();
  }

  /** The id and request hash of an order already stored under an external id. */
  public record ExistingOrder(UUID id, byte[] requestHash) {}
}
