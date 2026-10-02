package com.jacobian.orders.persistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Keyset position for {@code GET /v1/orders}: the last row's {@code (submitted_at, id)}. */
public record PageCursor(Instant submittedAt, UUID id) {

  public String encode() {
    String raw = submittedAt + "|" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Returns empty when the token was not produced by {@link #encode()}. */
  public static Optional<PageCursor> decode(String token) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      int bar = raw.indexOf('|');
      if (bar < 0) {
        return Optional.empty();
      }
      return Optional.of(
          new PageCursor(
              Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1))));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
