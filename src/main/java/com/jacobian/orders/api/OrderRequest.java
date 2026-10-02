package com.jacobian.orders.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * The {@code POST /v1/orders} body as received. Fields stay raw (strings, nullable) so that {@link
 * OrderRequestValidator} can report every problem at once. JSON names are snake_case.
 */
@Schema(
    description =
        "An order submission. Identifiers are trimmed, then compared exactly; lengths count Unicode"
            + " code points; NUL characters are rejected. Unknown fields are ignored.")
public record OrderRequest(
    @Schema(
            description = "Tenant submitting the order. The tenant must already exist.",
            requiredMode = RequiredMode.REQUIRED,
            format = "uuid",
            example = "11111111-1111-1111-1111-111111111111")
        String tenantId,
    @Schema(
            description =
                "The tenant's own order id: the idempotency key, unique per tenant. Trimmed,"
                    + " case-sensitive.",
            requiredMode = RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = OrderRequestValidator.ID_MAX,
            example = "PO-2026-001")
        String externalOrderId,
    @Schema(requiredMode = RequiredMode.REQUIRED) SubmittedBy submittedBy,
    @Schema(requiredMode = RequiredMode.REQUIRED) Patient patient,
    @Schema(
            description = "Order type.",
            requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"lab", "imaging", "medication", "consult"},
            example = "lab")
        String orderType,
    @Schema(
            description = "Order priority.",
            requiredMode = RequiredMode.REQUIRED,
            allowableValues = {"routine", "urgent", "stat"},
            example = "routine")
        String priority,
    @Schema(
            description = "Optional; if present it must be submitted.",
            allowableValues = {"submitted"},
            example = "submitted")
        String status,
    @ArraySchema(
            minItems = 1,
            arraySchema =
                @Schema(description = "Order lines.", requiredMode = RequiredMode.REQUIRED))
        List<Item> items,
    @Schema(
            description = "Free-text notes.",
            maxLength = OrderRequestValidator.NOTES_MAX,
            example = "Pre-op screening")
        String notes,
    @Schema(
            description =
                "ISO-8601 date-time with an offset, at most 24 hours in the future. Stored to"
                    + " microsecond precision.",
            requiredMode = RequiredMode.REQUIRED,
            format = "date-time",
            example = "2026-05-19T14:30:00.000Z")
        String submittedAt) {

  /** Who submitted the order. */
  @Schema(name = "OrderRequestSubmittedBy", description = "Who submitted the order.")
  public record SubmittedBy(
      @Schema(
              description = "Submitting user's id. Trimmed, case-sensitive.",
              requiredMode = RequiredMode.REQUIRED,
              minLength = 1,
              maxLength = OrderRequestValidator.ID_MAX,
              example = "u-001")
          String userId,
      @Schema(
              description = "Submitting user's display name.",
              maxLength = OrderRequestValidator.DISPLAY_NAME_MAX,
              example = "Jane Doe")
          String displayName) {}

  /** The patient, by the internal id the client looked up first. */
  @Schema(
      name = "OrderRequestPatient",
      description =
          "The patient, by the id the client looked up first. The patient must already exist for"
              + " the tenant; other patient fields are ignored.")
  public record Patient(
      @Schema(
              description = "Patient id within the tenant. Trimmed, case-sensitive.",
              requiredMode = RequiredMode.REQUIRED,
              minLength = 1,
              maxLength = OrderRequestValidator.ID_MAX,
              example = "P-345678")
          String patientId) {}

  /** One order line. */
  @Schema(name = "OrderRequestItem", description = "One order line.")
  public record Item(
      @Schema(
              description = "Item code.",
              requiredMode = RequiredMode.REQUIRED,
              minLength = 1,
              maxLength = OrderRequestValidator.ITEM_CODE_MAX,
              example = "CBC")
          String code,
      @Schema(
              description = "Item description.",
              maxLength = OrderRequestValidator.ITEM_DESCRIPTION_MAX,
              example = "Complete Blood Count")
          String description,
      @Schema(
              description = "Quantity; an integer, not a string or a fraction.",
              requiredMode = RequiredMode.REQUIRED,
              minimum = "1",
              example = "1")
          Integer quantity) {}
}
