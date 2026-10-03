package com.jacobian.orders.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A validated order submission: identifiers are trimmed, enums are resolved, an omitted status
 * defaults to {@link OrderStatus#SUBMITTED}, and {@code submittedAt} is truncated to microseconds
 * (what Postgres stores).
 */
public record OrderSubmission(
    UUID tenantId,
    String externalOrderId,
    String submittedByUserId,
    String submittedByDisplayName,
    String patientId,
    OrderType orderType,
    Priority priority,
    OrderStatus status,
    List<Item> items,
    String notes,
    Instant submittedAt) {

  public OrderSubmission {
    items = List.copyOf(items);
  }

  /** One line item, in submitted order. */
  public record Item(String code, String description, int quantity) {}
}
