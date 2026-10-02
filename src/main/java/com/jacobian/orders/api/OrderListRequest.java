package com.jacobian.orders.api;

import com.jacobian.orders.persistence.OrderPageQuery;
import com.jacobian.orders.persistence.PageCursor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Parsed {@code GET /orders} parameters: the tenant from the {@code X-Tenant-Id} header, an
 * optional {@code submitted_at} range ({@code submitted_from} inclusive, {@code submitted_to}
 * exclusive), a page size, and a cursor from the previous page.
 */
public record OrderListRequest(
    UUID tenantId, Instant submittedFrom, Instant submittedTo, PageCursor cursor, int limit) {

  public static final String TENANT_HEADER = "X-Tenant-Id";
  public static final int DEFAULT_LIMIT = 50;

  public static OrderListRequest parse(
      String tenantHeader, String submittedFrom, String submittedTo, String limit, String cursor) {
    List<FieldError> errors = new ArrayList<>();

    UUID tenantId = null;
    if (tenantHeader == null || tenantHeader.isBlank()) {
      errors.add(new FieldError(TENANT_HEADER, "required", "header is required"));
    } else {
      try {
        tenantId = UUID.fromString(tenantHeader.strip());
      } catch (IllegalArgumentException e) {
        errors.add(new FieldError(TENANT_HEADER, "invalid_format", "must be a UUID"));
      }
    }

    Instant from = parseInstant("submitted_from", submittedFrom, errors);
    Instant to = parseInstant("submitted_to", submittedTo, errors);
    if (from != null && to != null && from.isAfter(to)) {
      errors.add(
          new FieldError("submitted_from", "invalid_value", "must not be after submitted_to"));
    }

    int pageSize = DEFAULT_LIMIT;
    if (limit != null) {
      try {
        pageSize = Integer.parseInt(limit.strip());
        if (pageSize < 1 || pageSize > OrderPageQuery.MAX_LIMIT) {
          errors.add(
              new FieldError(
                  "limit", "out_of_range", "must be between 1 and " + OrderPageQuery.MAX_LIMIT));
        }
      } catch (NumberFormatException e) {
        errors.add(new FieldError("limit", "invalid_format", "must be an integer"));
      }
    }

    PageCursor after = null;
    if (cursor != null) {
      after = PageCursor.decode(cursor).orElse(null);
      if (after == null) {
        errors.add(
            new FieldError(
                "cursor", "invalid_value", "must be a next_cursor from a previous page"));
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidRequestException(errors);
    }
    return new OrderListRequest(tenantId, from, to, after, pageSize);
  }

  private static Instant parseInstant(String field, String value, List<FieldError> errors) {
    if (value == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      errors.add(
          new FieldError(
              field,
              "invalid_format",
              "must be an ISO-8601 date-time with an offset, e.g. 2026-05-19T14:30:00Z"));
      return null;
    }
  }
}
