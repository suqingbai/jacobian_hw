package com.jacobian.orders.api;

import com.jacobian.orders.persistence.OrderEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as stored, in the same snake_case shape as the request. */
public record OrderResponse(
    UUID id,
    UUID tenantId,
    String externalOrderId,
    SubmittedBy submittedBy,
    Patient patient,
    String orderType,
    String priority,
    String status,
    List<Item> items,
    String notes,
    Instant submittedAt,
    Instant createdAt) {

  /** Who submitted the order. */
  public record SubmittedBy(String userId, String displayName) {}

  /** The patient reference. */
  public record Patient(String patientId) {}

  /** One order line. */
  public record Item(String code, String description, int quantity) {}

  public static OrderResponse from(OrderEntity order) {
    return new OrderResponse(
        order.getId(),
        order.getTenantId(),
        order.getExternalOrderId(),
        new SubmittedBy(order.getSubmittedBy().id(), order.getSubmittedBy().name()),
        new Patient(order.getPatientId()),
        order.getOrderType().wireValue(),
        order.getPriority().wireValue(),
        order.getStatus(),
        order.getItems().stream()
            .map(item -> new Item(item.code(), item.description(), item.quantity()))
            .toList(),
        order.getNotes(),
        order.getSubmittedAt(),
        order.getCreatedAt());
  }
}
