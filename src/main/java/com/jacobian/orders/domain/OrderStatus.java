package com.jacobian.orders.domain;

import java.util.Arrays;
import java.util.Optional;

/** Order status: the API and the database both use the word. Only submission exists so far. */
public enum OrderStatus {
  SUBMITTED("submitted");

  private final String wireValue;

  OrderStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static Optional<OrderStatus> fromWireValue(String value) {
    return Arrays.stream(values()).filter(s -> s.wireValue.equals(value)).findFirst();
  }
}
