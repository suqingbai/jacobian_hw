package com.jacobian.orders.service;

import com.jacobian.orders.api.OrderConflictException;
import com.jacobian.orders.api.OrderListRequest;
import com.jacobian.orders.api.OrderPage;
import com.jacobian.orders.api.OrderResponse;
import com.jacobian.orders.api.UnknownReferenceException;
import com.jacobian.orders.domain.OrderSubmission;
import com.jacobian.orders.domain.RequestHash;
import com.jacobian.orders.persistence.OrderEntity;
import com.jacobian.orders.persistence.OrderPageQuery;
import com.jacobian.orders.persistence.OrderRepository;
import com.jacobian.orders.persistence.OrderWriter;
import com.jacobian.orders.persistence.PageCursor;
import com.jacobian.orders.persistence.PatientId;
import com.jacobian.orders.persistence.PatientRepository;
import com.jacobian.orders.persistence.TenantRepository;
import com.jacobian.orders.persistence.TenantSession;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order intake and query. Every transaction is bound to one tenant first, so row-level security
 * applies to every statement it runs (ADR-001).
 */
@Service
public class OrderService {

  private final TenantSession tenantSession;
  private final TenantRepository tenants;
  private final PatientRepository patients;
  private final OrderWriter writer;
  private final OrderRepository orders;
  private final OrderPageQuery pageQuery;

  public OrderService(
      TenantSession tenantSession,
      TenantRepository tenants,
      PatientRepository patients,
      OrderWriter writer,
      OrderRepository orders,
      OrderPageQuery pageQuery) {
    this.tenantSession = tenantSession;
    this.tenants = tenants;
    this.patients = patients;
    this.writer = writer;
    this.orders = orders;
    this.pageQuery = pageQuery;
  }

  /** The stored order and whether this call created it (201) or replayed it (200). */
  public record SubmitResult(OrderResponse order, boolean created) {}

  /**
   * Accepts a submission idempotently (ADR-002): a new {@code external_order_id} is stored; a
   * repeat with the same content returns the original order; a repeat with different content is a
   * conflict. Tenants and patients must already exist; this service never creates them.
   */
  @Transactional
  public SubmitResult submit(OrderSubmission submission) {
    UUID tenantId = submission.tenantId();
    tenantSession.bind(tenantId);
    if (!tenants.existsById(tenantId)) {
      throw UnknownReferenceException.unknownTenant("tenant_id");
    }
    if (!patients.existsById(new PatientId(tenantId, submission.patientId()))) {
      throw UnknownReferenceException.unknownPatient();
    }

    byte[] hash = RequestHash.of(submission);
    Optional<UUID> created = writer.insertOrder(submission, hash);
    if (created.isPresent()) {
      writer.insertItems(tenantId, created.get(), submission.items());
      return new SubmitResult(load(created.get()), true);
    }

    OrderWriter.ExistingOrder existing =
        writer
            .findByExternalId(tenantId, submission.externalOrderId())
            .orElseThrow(() -> new IllegalStateException("ON CONFLICT without a conflicting row"));
    if (!MessageDigest.isEqual(existing.requestHash(), hash)) {
      throw new OrderConflictException(submission.externalOrderId(), existing.id());
    }
    return new SubmitResult(load(existing.id()), false);
  }

  /** One newest-first page of the tenant's orders. */
  @Transactional(readOnly = true)
  public OrderPage list(OrderListRequest request) {
    tenantSession.bind(request.tenantId());
    if (!tenants.existsById(request.tenantId())) {
      throw UnknownReferenceException.unknownTenant(OrderListRequest.TENANT_HEADER);
    }
    List<OrderEntity> rows =
        pageQuery.fetch(
            request.tenantId(),
            request.submittedFrom(),
            request.submittedTo(),
            request.cursor(),
            request.limit() + 1);
    boolean hasMore = rows.size() > request.limit();
    List<OrderEntity> page = hasMore ? rows.subList(0, request.limit()) : rows;
    String nextCursor = null;
    if (hasMore) {
      OrderEntity last = page.get(page.size() - 1);
      nextCursor = new PageCursor(last.getSubmittedAt(), last.getId()).encode();
    }
    return new OrderPage(page.stream().map(OrderResponse::from).toList(), nextCursor);
  }

  private OrderResponse load(UUID orderId) {
    return orders
        .findById(orderId)
        .map(OrderResponse::from)
        .orElseThrow(() -> new IllegalStateException("Order " + orderId + " vanished"));
  }
}
