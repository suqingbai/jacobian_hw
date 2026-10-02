package com.jacobian.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * OpenAPI schemas for the RFC 9457 problem documents {@link ApiExceptionHandler} returns. These
 * types only document the wire shape; the handler builds {@code ProblemDetail}s.
 */
final class ApiProblem {

  private ApiProblem() {}

  static final String MEDIA_TYPE = "application/problem+json";

  /** A 400 or 422 problem with field-level errors. */
  @Schema(
      name = "Problem",
      description = "RFC 9457 problem document listing every field-level problem found.")
  record WithErrors(
      @Schema(
              description = "Problem type URI.",
              requiredMode = RequiredMode.REQUIRED,
              example = "about:blank")
          URI type,
      @Schema(
              description = "Short summary of the problem type.",
              requiredMode = RequiredMode.REQUIRED,
              example = "Invalid request")
          String title,
      @Schema(
              description = "HTTP status code.",
              requiredMode = RequiredMode.REQUIRED,
              example = "400")
          int status,
      @Schema(
              description = "Explanation of this occurrence.",
              requiredMode = RequiredMode.REQUIRED,
              example = "The request has invalid fields.")
          String detail,
      @Schema(
              description = "Request path.",
              requiredMode = RequiredMode.REQUIRED,
              example = "/v1/orders")
          URI instance,
      @Schema(
              description = "Every field-level problem found in the request.",
              requiredMode = RequiredMode.REQUIRED)
          List<FieldError> errors) {}

  /** The 409 problem for a resubmission with different content. */
  @Schema(
      name = "ConflictProblem",
      description =
          "RFC 9457 problem document for an external_order_id the tenant already used with"
              + " different content.")
  record Conflict(
      @Schema(
              description = "Problem type URI.",
              requiredMode = RequiredMode.REQUIRED,
              example = "about:blank")
          URI type,
      @Schema(
              description = "Short summary of the problem type.",
              requiredMode = RequiredMode.REQUIRED,
              example = "Conflicting resubmission")
          String title,
      @Schema(
              description = "HTTP status code.",
              requiredMode = RequiredMode.REQUIRED,
              example = "409")
          int status,
      @Schema(
              description = "Explanation of this occurrence.",
              requiredMode = RequiredMode.REQUIRED,
              example =
                  "An order with external_order_id 'PO-2026-001' already exists for this tenant"
                      + " with different content")
          String detail,
      @Schema(
              description = "Request path.",
              requiredMode = RequiredMode.REQUIRED,
              example = "/v1/orders")
          URI instance,
      @Schema(
              description = "Id of the order already stored under this external_order_id.",
              requiredMode = RequiredMode.REQUIRED,
              example = "0196e6a4-3b2c-7d4e-9f10-2a3b4c5d6e7f")
          UUID existingOrderId) {}
}
