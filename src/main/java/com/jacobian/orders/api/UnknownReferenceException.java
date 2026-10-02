package com.jacobian.orders.api;

import java.util.List;

/**
 * The request is well-formed but names a tenant or patient that does not exist: 422 Unprocessable
 * Content. Tenants and patients are maintained outside this service.
 */
public class UnknownReferenceException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public UnknownReferenceException(FieldError error) {
    super(error.message());
    this.errors = List.of(error);
  }

  public List<FieldError> errors() {
    return errors;
  }

  public static UnknownReferenceException unknownTenant(String field) {
    return new UnknownReferenceException(new FieldError(field, "unknown_tenant", "no such tenant"));
  }

  public static UnknownReferenceException unknownPatient() {
    return new UnknownReferenceException(
        new FieldError("patient.patient_id", "unknown_patient", "no such patient for this tenant"));
  }
}
