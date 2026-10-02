package com.jacobian.orders.domain;

import java.util.Arrays;
import java.util.Optional;

/** Order type: the API uses the word, the database stores a one-letter code. */
public enum OrderType {
  LAB("lab", 'L'),
  IMAGING("imaging", 'I'),
  MEDICATION("medication", 'M'),
  CONSULT("consult", 'C');

  private final String wireValue;
  private final char code;

  OrderType(String wireValue, char code) {
    this.wireValue = wireValue;
    this.code = code;
  }

  public String wireValue() {
    return wireValue;
  }

  public char code() {
    return code;
  }

  public static Optional<OrderType> fromWireValue(String value) {
    return Arrays.stream(values()).filter(t -> t.wireValue.equals(value)).findFirst();
  }

  public static OrderType fromCode(char code) {
    return Arrays.stream(values())
        .filter(t -> t.code == code)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown order_type code: " + code));
  }
}
