package com.jacobian.orders.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SHA-256 over the content that makes two submissions "the same order" (ADR-002).
 *
 * <p>Included: patient_id, order_type, priority, the effective status (after an omitted status
 * defaults to submitted), notes, and the items (code, description, quantity) sorted so their order
 * does not matter. Excluded: tenant_id and external_order_id (they are the idempotency key),
 * submitted_at, and submitted_by.
 *
 * <p>The input is a JSON array in a fixed field order, built from validated, trimmed values, so
 * JSON formatting, key order, and absent-versus-null optional fields cannot change it.
 */
public final class RequestHash {

  /** Bumped only if the coverage or encoding ever changes. */
  private static final String VERSION = "v1";

  private static final ObjectMapper CANONICAL_JSON = new ObjectMapper();

  private static final Comparator<OrderSubmission.Item> ITEM_ORDER =
      Comparator.comparing(OrderSubmission.Item::code)
          .thenComparing(
              OrderSubmission.Item::description, Comparator.nullsFirst(Comparator.naturalOrder()))
          .thenComparingInt(OrderSubmission.Item::quantity);

  private RequestHash() {}

  public static byte[] of(OrderSubmission submission) {
    List<Object> items = new ArrayList<>();
    submission.items().stream()
        .sorted(ITEM_ORDER)
        .forEach(
            item -> {
              List<Object> line = new ArrayList<>();
              line.add(item.code());
              line.add(item.description());
              line.add(item.quantity());
              items.add(line);
            });

    List<Object> canonical = new ArrayList<>();
    canonical.add(VERSION);
    canonical.add(submission.patientId());
    canonical.add(submission.orderType().wireValue());
    canonical.add(submission.priority().wireValue());
    canonical.add(submission.status().wireValue());
    canonical.add(submission.notes());
    canonical.add(items);
    return sha256(toJson(canonical));
  }

  private static byte[] toJson(List<Object> canonical) {
    try {
      return CANONICAL_JSON.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode a list of strings and numbers", e);
    }
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required on every Java platform", e);
    }
  }
}
