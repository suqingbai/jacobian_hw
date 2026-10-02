package com.jacobian.orders.domain;

import java.util.Arrays;
import java.util.Optional;

/** Order priority: the API uses the word, the database stores a one-letter code. */
public enum Priority {
  ROUTINE("routine", 'R'),
  URGENT("urgent", 'U'),
  STAT("stat", 'S');

  private final String wireValue;
  private final char code;

  Priority(String wireValue, char code) {
    this.wireValue = wireValue;
    this.code = code;
  }

  public String wireValue() {
    return wireValue;
  }

  public char code() {
    return code;
  }

  public static Optional<Priority> fromWireValue(String value) {
    return Arrays.stream(values()).filter(p -> p.wireValue.equals(value)).findFirst();
  }

  public static Priority fromCode(char code) {
    return Arrays.stream(values())
        .filter(p -> p.code == code)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown priority code: " + code));
  }
}
