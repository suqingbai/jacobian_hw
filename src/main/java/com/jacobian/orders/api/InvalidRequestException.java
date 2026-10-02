package com.jacobian.orders.api;

import java.util.List;

/** The request is malformed or breaks a validation rule: 400 Bad Request. */
public class InvalidRequestException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public InvalidRequestException(List<FieldError> errors) {
    super("Invalid request: " + errors.size() + " field error(s)");
    this.errors = List.copyOf(errors);
  }

  public List<FieldError> errors() {
    return errors;
  }
}
