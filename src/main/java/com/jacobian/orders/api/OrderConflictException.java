package com.jacobian.orders.api;

import java.util.UUID;

/**
 * The tenant already has an order with this {@code external_order_id} but different content: 409
 * Conflict. The response names the existing order.
 */
public class OrderConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final UUID existingOrderId;

  public OrderConflictException(String externalOrderId, UUID existingOrderId) {
    super(
        "An order with external_order_id '"
            + externalOrderId
            + "' already exists for this tenant with different content");
    this.existingOrderId = existingOrderId;
  }

  public UUID existingOrderId() {
    return existingOrderId;
  }
}
