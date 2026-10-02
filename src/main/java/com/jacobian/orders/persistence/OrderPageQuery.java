package com.jacobian.orders.persistence;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

/**
 * Newest-first keyset pagination over {@code orders_tenant_submitted_at_idx (tenant_id,
 * submitted_at DESC, id DESC)}. The row comparison {@code (submitted_at, id) < (...)} is an index
 * condition, so every page reads only the rows it returns.
 */
@Repository
public class OrderPageQuery {

  public static final int MAX_LIMIT = 100;

  private final EntityManager entityManager;

  public OrderPageQuery(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /** Returns up to {@code fetchSize} orders; {@code from} is inclusive and {@code to} exclusive. */
  public List<OrderEntity> fetch(
      UUID tenantId, Instant from, Instant to, PageCursor after, int fetchSize) {
    StringBuilder sql =
        new StringBuilder("SELECT * FROM order_service.orders WHERE tenant_id = :tenantId");
    if (from != null) {
      sql.append(" AND submitted_at >= :from");
    }
    if (to != null) {
      sql.append(" AND submitted_at < :to");
    }
    if (after != null) {
      sql.append(" AND (submitted_at, id) < (:afterSubmittedAt, :afterId)");
    }
    sql.append(" ORDER BY submitted_at DESC, id DESC LIMIT :fetchSize");

    NativeQuery<OrderEntity> query =
        entityManager.unwrap(Session.class).createNativeQuery(sql.toString(), OrderEntity.class);
    query.setParameter("tenantId", tenantId);
    if (from != null) {
      query.setParameter("from", from);
    }
    if (to != null) {
      query.setParameter("to", to);
    }
    if (after != null) {
      query.setParameter("afterSubmittedAt", after.submittedAt());
      query.setParameter("afterId", after.id());
    }
    query.setParameter("fetchSize", fetchSize);
    return query.getResultList();
  }
}
