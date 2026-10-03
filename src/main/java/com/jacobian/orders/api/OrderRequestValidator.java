package com.jacobian.orders.api;

import com.jacobian.orders.domain.OrderStatus;
import com.jacobian.orders.domain.OrderSubmission;
import com.jacobian.orders.domain.OrderType;
import com.jacobian.orders.domain.Priority;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates a {@code POST /v1/orders} body and turns it into an {@link OrderSubmission}, reporting
 * every problem at once.
 *
 * <p>Rules (assignment plus the approved schema): identifiers are trimmed before checking and
 * comparing; lengths count Unicode code points, as Postgres {@code char_length} does; NUL is
 * rejected because Postgres text cannot store it; {@code submitted_at} needs an offset and may be
 * at most 24 hours in the future.
 */
@Component
public class OrderRequestValidator {

  static final int ID_MAX = 100;
  static final int DISPLAY_NAME_MAX = 50;
  static final int ITEM_CODE_MAX = 64;
  static final int ITEM_DESCRIPTION_MAX = 500;
  static final int NOTES_MAX = 2000;
  static final Duration MAX_FUTURE_SKEW = Duration.ofHours(24);

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private final Clock clock;

  public OrderRequestValidator(Clock clock) {
    this.clock = clock;
  }

  public OrderSubmission validate(OrderRequest request) {
    List<FieldError> errors = new ArrayList<>();

    UUID tenantId = parseUuid("tenant_id", request.tenantId(), errors).orElse(null);
    String externalOrderId = requiredId("external_order_id", request.externalOrderId(), errors);

    String userId = null;
    String displayName = null;
    if (request.submittedBy() == null) {
      errors.add(required("submitted_by"));
    } else {
      userId = requiredId("submitted_by.user_id", request.submittedBy().userId(), errors);
      displayName =
          optionalText(
              "submitted_by.display_name",
              request.submittedBy().displayName(),
              DISPLAY_NAME_MAX,
              errors);
    }

    String patientId = null;
    if (request.patient() == null) {
      errors.add(required("patient"));
    } else {
      patientId = requiredId("patient.patient_id", request.patient().patientId(), errors);
    }

    OrderType orderType =
        parseEnum(
                "order_type",
                request.orderType(),
                OrderType.fromWireValue(request.orderType()),
                "lab, imaging, medication, consult",
                errors)
            .orElse(null);
    Priority priority =
        parseEnum(
                "priority",
                request.priority(),
                Priority.fromWireValue(request.priority()),
                "routine, urgent, stat",
                errors)
            .orElse(null);

    OrderStatus status = OrderStatus.SUBMITTED;
    if (request.status() != null) {
      status = OrderStatus.fromWireValue(request.status()).orElse(null);
      if (status != OrderStatus.SUBMITTED) {
        errors.add(
            new FieldError(
                "status", "invalid_value", "must be 'submitted' (or omitted) on submission"));
      }
    }

    List<OrderSubmission.Item> items = validateItems(request.items(), errors);
    String notes = optionalText("notes", request.notes(), NOTES_MAX, errors);
    Instant submittedAt = validateSubmittedAt(request.submittedAt(), errors);

    if (!errors.isEmpty()) {
      throw new InvalidRequestException(errors);
    }
    return new OrderSubmission(
        tenantId,
        externalOrderId,
        userId,
        displayName,
        patientId,
        orderType,
        priority,
        status,
        items,
        notes,
        submittedAt);
  }

  private List<OrderSubmission.Item> validateItems(
      List<OrderRequest.Item> items, List<FieldError> errors) {
    if (items == null || items.isEmpty()) {
      errors.add(new FieldError("items", "required", "must contain at least one item"));
      return List.of();
    }
    List<OrderSubmission.Item> valid = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      String path = "items[" + i + "]";
      OrderRequest.Item item = items.get(i);
      if (item == null) {
        errors.add(required(path));
        continue;
      }
      String code = requiredText(path + ".code", item.code(), ITEM_CODE_MAX, errors);
      String description =
          optionalText(path + ".description", item.description(), ITEM_DESCRIPTION_MAX, errors);
      Integer quantity = item.quantity();
      if (quantity == null) {
        errors.add(required(path + ".quantity"));
      } else if (quantity < 1) {
        errors.add(new FieldError(path + ".quantity", "too_small", "must be at least 1"));
      }
      if (code != null && quantity != null && quantity >= 1) {
        valid.add(new OrderSubmission.Item(code, description, quantity));
      }
    }
    return valid;
  }

  private Instant validateSubmittedAt(String value, List<FieldError> errors) {
    if (value == null) {
      errors.add(required("submitted_at"));
      return null;
    }
    Instant instant;
    try {
      instant =
          OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
              .toInstant()
              .truncatedTo(ChronoUnit.MICROS);
    } catch (DateTimeParseException e) {
      errors.add(
          new FieldError(
              "submitted_at",
              "invalid_format",
              "must be an ISO-8601 date-time with an offset, e.g. 2026-05-19T14:30:00Z"));
      return null;
    }
    if (instant.isAfter(clock.instant().plus(MAX_FUTURE_SKEW))) {
      errors.add(
          new FieldError(
              "submitted_at", "in_future", "must not be more than 24 hours in the future"));
    }
    return instant;
  }

  private static Optional<UUID> parseUuid(String field, String value, List<FieldError> errors) {
    if (value == null || value.isEmpty()) {
      errors.add(required(field));
      return Optional.empty();
    }
    if (!UUID_PATTERN.matcher(value).matches()) {
      errors.add(new FieldError(field, "invalid_format", "must be a UUID"));
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(value));
  }

  private static <E> Optional<E> parseEnum(
      String field, String value, Optional<E> parsed, String allowed, List<FieldError> errors) {
    if (value == null) {
      errors.add(required(field));
    } else if (parsed.isEmpty()) {
      errors.add(new FieldError(field, "invalid_value", "must be one of: " + allowed));
    }
    return parsed;
  }

  /** An identifier: trimmed, then 1 to {@link #ID_MAX} code points. */
  private static String requiredId(String field, String value, List<FieldError> errors) {
    String trimmed = value == null ? null : value.strip();
    return requiredText(field, trimmed, ID_MAX, errors);
  }

  private static String requiredText(String field, String value, int max, List<FieldError> errors) {
    if (value == null || value.isBlank()) {
      errors.add(required(field));
      return null;
    }
    return checkText(field, value, max, errors) ? value : null;
  }

  private static String optionalText(String field, String value, int max, List<FieldError> errors) {
    if (value == null) {
      return null;
    }
    return checkText(field, value, max, errors) ? value : null;
  }

  private static boolean checkText(String field, String value, int max, List<FieldError> errors) {
    if (value.indexOf('\u0000') >= 0) {
      errors.add(new FieldError(field, "invalid_character", "must not contain NUL characters"));
      return false;
    }
    if (value.codePointCount(0, value.length()) > max) {
      errors.add(new FieldError(field, "too_long", "must be at most " + max + " characters"));
      return false;
    }
    return true;
  }

  private static FieldError required(String field) {
    return new FieldError(field, "required", "is required");
  }
}
