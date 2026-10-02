package com.jacobian.orders.api;

import com.jacobian.orders.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code POST /orders} and {@code GET /orders}. */
@RestController
@RequestMapping("/orders")
public class OrderController {

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
  @PostMapping
  public ResponseEntity<OrderResponse> submit(@RequestBody OrderRequest body) {
    OrderService.SubmitResult result = orders.submit(validator.validate(body));
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(result.order());
  }

  /** The caller's tenant comes from the {@code X-Tenant-Id} header (temporary; see README). */
  @GetMapping
  public OrderPage list(
      @RequestHeader(name = OrderListRequest.TENANT_HEADER, required = false) String tenantId,
      @RequestParam(name = "submitted_from", required = false) String submittedFrom,
      @RequestParam(name = "submitted_to", required = false) String submittedTo,
      @RequestParam(name = "limit", required = false) String limit,
      @RequestParam(name = "cursor", required = false) String cursor) {
    return orders.list(OrderListRequest.parse(tenantId, submittedFrom, submittedTo, limit, cursor));
  }
}
