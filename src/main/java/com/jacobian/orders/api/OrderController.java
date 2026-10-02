package com.jacobian.orders.api;

import com.jacobian.orders.persistence.OrderPageQuery;
import com.jacobian.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /v1/orders} and {@code GET /v1/orders}. */
@RestController
@RequestMapping("/v1/orders")
@Tag(name = "Orders", description = "Submit orders idempotently and list a tenant's orders.")
public class OrderController {

  private static final String INVALID_REQUEST_EXAMPLE =
      """
      {"type": "about:blank", "title": "Invalid request", "status": 400,
       "detail": "The request has invalid fields.", "instance": "/v1/orders",
       "errors": [{"field": "items[0].quantity", "code": "too_small",
                   "message": "must be at least 1"}]}""";

  private static final String UNKNOWN_PATIENT_EXAMPLE =
      """
      {"type": "about:blank", "title": "Unknown reference", "status": 422,
       "detail": "The request refers to a tenant or patient that does not exist.",
       "instance": "/v1/orders",
       "errors": [{"field": "patient.patient_id", "code": "unknown_patient",
                   "message": "no such patient for this tenant"}]}""";

  private static final String UNKNOWN_TENANT_HEADER_EXAMPLE =
      """
      {"type": "about:blank", "title": "Unknown reference", "status": 422,
       "detail": "The request refers to a tenant or patient that does not exist.",
       "instance": "/v1/orders",
       "errors": [{"field": "X-Tenant-Id", "code": "unknown_tenant",
                   "message": "no such tenant"}]}""";

  private static final String INVALID_LIST_EXAMPLE =
      """
      {"type": "about:blank", "title": "Invalid request", "status": 400,
       "detail": "The request has invalid fields.", "instance": "/v1/orders",
       "errors": [{"field": "limit", "code": "out_of_range",
                   "message": "must be between 1 and 100"}]}""";

  private final OrderRequestValidator validator;
  private final OrderService orders;

  public OrderController(OrderRequestValidator validator, OrderService orders) {
    this.validator = validator;
    this.orders = orders;
  }

  /**
   * 201 with the new order; 200 with the original order for an identical resubmission; 409 for a
   * conflicting resubmission; 422 for an unknown tenant or patient; 400 for invalid input.
   */
  @Operation(
      summary = "Submit an order",
      description =
          """
          Stores a new order, idempotently on `(tenant_id, external_order_id)` (ADR-002). An \
          identical resubmission returns the original order with 200; content is compared by a \
          hash of `patient.patient_id`, `order_type`, `priority`, `notes`, and `items` (in any \
          order), so `submitted_at`, `submitted_by`, and `status` do not count. The same \
          `external_order_id` with different content is a 409. The tenant and the patient must \
          already exist. Every validation problem is reported at once.""")
  @ApiResponse(
      responseCode = "201",
      description = "Created: a new order, returned as stored.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = OrderResponse.class)))
  @ApiResponse(
      responseCode = "200",
      description =
          "Replay: the tenant already has this external_order_id with the same content. Returns"
              + " the original order as stored (its id, submitted_at, and submitter).",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = OrderResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Invalid or malformed input; errors lists every problem.",
      content =
          @Content(
              mediaType = ApiProblem.MEDIA_TYPE,
              schema = @Schema(implementation = ApiProblem.WithErrors.class),
              examples = @ExampleObject(value = INVALID_REQUEST_EXAMPLE)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Conflict: the tenant already has this external_order_id with different content."
              + " existing_order_id names the stored order.",
      content =
          @Content(
              mediaType = ApiProblem.MEDIA_TYPE,
              schema = @Schema(implementation = ApiProblem.Conflict.class)))
  @ApiResponse(
      responseCode = "422",
      description =
          "The tenant or the patient does not exist: errors[].code is unknown_tenant (field"
              + " tenant_id) or unknown_patient (field patient.patient_id).",
      content =
          @Content(
              mediaType = ApiProblem.MEDIA_TYPE,
              schema = @Schema(implementation = ApiProblem.WithErrors.class)))
  @PostMapping
  public ResponseEntity<OrderResponse> submit(@RequestBody OrderRequest body) {
    OrderService.SubmitResult result = orders.submit(validator.validate(body));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.order());
  }

  /** The caller's tenant comes from the {@code X-Tenant-Id} header (temporary; see README). */
  @Operation(
      summary = "List the tenant's orders",
      description =
          """
          Lists the calling tenant's orders, newest first by `submitted_at`, then `id`, \
          optionally filtered to a `submitted_at` range. Keyset pagination: pass `next_cursor` \
          back as `cursor` for the next page; it is null on the last page. Pages stay stable \
          while new orders arrive.""")
  @ApiResponse(
      responseCode = "200",
      description = "One page of orders.",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = OrderPage.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "Missing or invalid X-Tenant-Id, or an invalid submitted_from, submitted_to, limit, or"
              + " cursor; errors lists every problem.",
      content =
          @Content(
              mediaType = ApiProblem.MEDIA_TYPE,
              schema = @Schema(implementation = ApiProblem.WithErrors.class),
              examples = @ExampleObject(value = INVALID_LIST_EXAMPLE)))
  @ApiResponse(
      responseCode = "422",
      description =
          "The tenant does not exist: errors[].code is unknown_tenant (field X-Tenant-Id).",
      content =
          @Content(
              mediaType = ApiProblem.MEDIA_TYPE,
              schema = @Schema(implementation = ApiProblem.WithErrors.class),
              examples = @ExampleObject(value = UNKNOWN_TENANT_HEADER_EXAMPLE)))
  @GetMapping
  public OrderPage list(
      @Parameter(
              in = ParameterIn.HEADER,
              required = true,
              description =
                  "The calling tenant. Unauthenticated for now; in production the tenant comes"
                      + " from a verified JWT claim.",
              schema = @Schema(type = "string", format = "uuid"),
              example = "11111111-1111-1111-1111-111111111111")
          @RequestHeader(name = OrderListRequest.TENANT_HEADER, required = false)
          String tenantId,
      @Parameter(
              description =
                  "Inclusive lower bound on submitted_at: an ISO-8601 date-time with an offset."
                      + " Use Z, or URL-encode a + offset.",
              schema = @Schema(type = "string", format = "date-time"),
              example = "2026-05-01T00:00:00Z")
          @RequestParam(name = "submitted_from", required = false)
          String submittedFrom,
      @Parameter(
              description =
                  "Exclusive upper bound on submitted_at: an ISO-8601 date-time with an offset."
                      + " Must not be before submitted_from.",
              schema = @Schema(type = "string", format = "date-time"),
              example = "2026-06-01T00:00:00Z")
          @RequestParam(name = "submitted_to", required = false)
          String submittedTo,
      @Parameter(
              description = "Page size.",
              schema =
                  @Schema(
                      type = "integer",
                      minimum = "1",
                      maximum = "" + OrderPageQuery.MAX_LIMIT,
                      defaultValue = "" + OrderListRequest.DEFAULT_LIMIT))
          @RequestParam(name = "limit", required = false)
          String limit,
      @Parameter(
              description = "The next_cursor from the previous page; omit for the first page.",
              schema = @Schema(type = "string"))
          @RequestParam(name = "cursor", required = false)
          String cursor) {
    return orders.list(OrderListRequest.parse(tenantId, submittedFrom, submittedTo, limit, cursor));
  }
}
