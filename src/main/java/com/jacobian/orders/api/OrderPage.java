package com.jacobian.orders.api;

import java.util.List;

/** One page of {@code GET /orders}, newest first. {@code nextCursor} is null on the last page. */
public record OrderPage(List<OrderResponse> orders, String nextCursor) {}
