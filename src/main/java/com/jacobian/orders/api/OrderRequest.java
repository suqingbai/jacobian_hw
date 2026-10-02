package com.jacobian.orders.api;

import java.util.List;

/**
 * The {@code POST /orders} body as received. Fields stay raw (strings, nullable) so that {@link
 * OrderRequestValidator} can report every problem at once. JSON names are snake_case.
 */
public record OrderRequest(
    String tenantId,
    String externalOrderId,
    SubmittedBy submittedBy,
    Patient patient,
    String orderType,
    String priority,
    String status,
    List<Item> items,
    String notes,
    String submittedAt) {

  /** Who submitted the order. */
  public record SubmittedBy(String userId, String displayName) {}

  /** The patient, by the internal id the client looked up first. */
  public record Patient(String patientId) {}

  /** One order line. */
  public record Item(String code, String description, Integer quantity) {}
}
