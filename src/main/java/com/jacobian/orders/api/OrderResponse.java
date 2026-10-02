package com.jacobian.orders.api;

import com.jacobian.orders.persistence.OrderEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as stored, in the same snake_case shape as the request. */
@Schema(description = "An order as stored, in the same shape as the request.")
public record OrderResponse(
    @Schema(
            description = "Server-assigned order id.",
            requiredMode = RequiredMode.REQUIRED,
            example = "0196e6a4-3b2c-7d4e-9f10-2a3b4c5d6e7f")
        UUID id,
    @Schema(
            description = "Owning tenant.",
            requiredMode = RequiredMode.REQUIRED,
            example = "11111111-1111-1111-1111-111111111111")
        UUID tenantId,
    @Schema(
            description = "The tenant's own order id, trimmed.",
            requiredMode = RequiredMode.REQUIRED,
            example = "PO-2026-001")
        String externalOrderId,
    @Schema(requiredMode = RequiredMode.REQUIRED) SubmittedBy submittedBy,
    @Schema(requiredMode = RequiredMode.REQUIRED) Patient patient,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"lab", "imaging", "medication", "consult"},
            example = "lab")
        String orderType,
    @Schema(
            requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"routine", "urgent", "stat"},
            example = "routine")
        String priority,
    @Schema(
            description = "Order status.",
            requiredMode = RequiredMode.REQUIRED,
            example = "submitted")
        String status,
    @Schema(requiredMode = RequiredMode.REQUIRED) List<Item> items,
    @Schema(
            description = "Free-text notes.",
            types = {"string", "null"},
            example = "Pre-op screening")
        String notes,
    @Schema(
            description = "When the client submitted the order, to microsecond precision.",
            requiredMode = RequiredMode.REQUIRED,
            example = "2026-05-19T14:30:00Z")
        Instant submittedAt,
    @Schema(
            description = "When the service stored the order.",
            requiredMode = RequiredMode.REQUIRED,
            example = "2026-05-19T14:30:01.123456Z")
        Instant createdAt) {

  /** Who submitted the order. */
  @Schema(name = "OrderResponseSubmittedBy", description = "Who submitted the order.")
  public record SubmittedBy(
      @Schema(requiredMode = RequiredMode.REQUIRED, example = "u-001") String userId,
      @Schema(
              types = {"string", "null"},
              example = "Jane Doe")
          String displayName) {}

  /** The patient reference. */
  @Schema(name = "OrderResponsePatient", description = "The patient reference.")
  public record Patient(
      @Schema(requiredMode = RequiredMode.REQUIRED, example = "P-345678") String patientId) {}

  /** One order line. */
  @Schema(name = "OrderResponseItem", description = "One order line.")
  public record Item(
      @Schema(requiredMode = RequiredMode.REQUIRED, example = "CBC") String code,
      @Schema(
              types = {"string", "null"},
              example = "Complete Blood Count")
          String description,
      @Schema(requiredMode = RequiredMode.REQUIRED, minimum = "1", example = "1") int quantity) {}

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
