package com.jacobian.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * One page of {@code GET /v1/orders}, newest first. {@code nextCursor} is null on the last page.
 */
@Schema(description = "One page of the tenant's orders, newest first by submitted_at, then id.")
public record OrderPage(
    @Schema(description = "The orders on this page.") List<OrderResponse> orders,
    @Schema(
            description =
                "Opaque cursor for the next page; pass it back as the cursor query parameter."
                    + " Null on the last page.",
            types = {"string", "null"},
            example =
                "MjAyNi0wNS0xOVQxNDozMDowMFp8MDE5NmU2YTQtM2IyYy03ZDRlLTlmMTAtMmEzYjRjNWQ2ZTdm")
        String nextCursor) {}
