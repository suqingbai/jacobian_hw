package com.jacobian.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestHashTests {

  private static final OrderSubmission BASE =
      new OrderSubmission(
          UUID.fromString("11111111-1111-1111-1111-111111111111"),
          "PO-1",
          "u-001",
          "Jane Doe",
          "P-345678",
          OrderType.LAB,
          Priority.ROUTINE,
          List.of(
              new OrderSubmission.Item("CBC", "Complete Blood Count", 1),
              new OrderSubmission.Item("BMP", null, 2)),
          "Pre-op screening",
          Instant.parse("2026-05-19T14:30:00Z"));

  @Test
  void isA32ByteSha256() {
    assertThat(RequestHash.of(BASE)).hasSize(32);
  }

  @Test
  void ignoresTheIdempotencyKeySubmitterSubmittedAtAndItemOrder() {
    OrderSubmission variant =
        new OrderSubmission(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "PO-OTHER",
            "u-999",
            "Someone Else",
            BASE.patientId(),
            BASE.orderType(),
            BASE.priority(),
            List.of(BASE.items().get(1), BASE.items().get(0)),
            BASE.notes(),
            Instant.parse("2020-01-01T00:00:00Z"));

    assertThat(RequestHash.of(variant)).isEqualTo(RequestHash.of(BASE));
  }

  @Test
  void changesWithEveryCoveredField() {
    byte[] base = RequestHash.of(BASE);

    assertThat(
            RequestHash.of(
                with(
                    BASE,
                    "P-OTHER",
                    BASE.orderType(),
                    BASE.priority(),
                    BASE.items(),
                    BASE.notes())))
        .isNotEqualTo(base);
    assertThat(
            RequestHash.of(
                with(
                    BASE,
                    BASE.patientId(),
                    OrderType.IMAGING,
                    BASE.priority(),
                    BASE.items(),
                    BASE.notes())))
        .isNotEqualTo(base);
    assertThat(
            RequestHash.of(
                with(
                    BASE,
                    BASE.patientId(),
                    BASE.orderType(),
                    Priority.STAT,
                    BASE.items(),
                    BASE.notes())))
        .isNotEqualTo(base);
    assertThat(
            RequestHash.of(
                with(
                    BASE, BASE.patientId(), BASE.orderType(), BASE.priority(), BASE.items(), null)))
        .isNotEqualTo(base);
    assertThat(
            RequestHash.of(
                with(
                    BASE,
                    BASE.patientId(),
                    BASE.orderType(),
                    BASE.priority(),
                    List.of(new OrderSubmission.Item("CBC", "Complete Blood Count", 2)),
                    BASE.notes())))
        .isNotEqualTo(base);
  }

  @Test
  void repeatedItemsStillCount() {
    OrderSubmission.Item cbc = new OrderSubmission.Item("CBC", null, 1);
    byte[] once =
        RequestHash.of(
            with(
                BASE,
                BASE.patientId(),
                BASE.orderType(),
                BASE.priority(),
                List.of(cbc),
                BASE.notes()));
    byte[] twice =
        RequestHash.of(
            with(
                BASE,
                BASE.patientId(),
                BASE.orderType(),
                BASE.priority(),
                List.of(cbc, cbc),
                BASE.notes()));

    assertThat(twice).isNotEqualTo(once);
  }

  private static OrderSubmission with(
      OrderSubmission base,
      String patientId,
      OrderType orderType,
      Priority priority,
      List<OrderSubmission.Item> items,
      String notes) {
    return new OrderSubmission(
        base.tenantId(),
        base.externalOrderId(),
        base.submittedByUserId(),
        base.submittedByDisplayName(),
        patientId,
        orderType,
        priority,
        items,
        notes,
        base.submittedAt());
  }
}
