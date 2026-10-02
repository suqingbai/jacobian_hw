package com.jacobian.orders.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 9457 problem responses. Field-level problems go in an {@code errors} array
 * of {@code {field, code, message}}. Database error details are never echoed: they can contain PHI.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** Foreign keys whose violation means "unknown reference" if a row disappears mid-request. */
  private static final Map<String, UnknownReferenceException> FK_TO_UNKNOWN_REFERENCE =
      Map.of(
          "orders_patient_fk", UnknownReferenceException.unknownPatient(),
          "orders_tenant_id_fkey", UnknownReferenceException.unknownTenant("tenant_id"));

  @ExceptionHandler(InvalidRequestException.class)
  ProblemDetail invalidRequest(InvalidRequestException e) {
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "The request has invalid fields.", e.errors());
  }

  @ExceptionHandler(UnknownReferenceException.class)
  ProblemDetail unknownReference(UnknownReferenceException e) {
    return unknownReferenceProblem(e);
  }

  @ExceptionHandler(OrderConflictException.class)
  ProblemDetail conflict(OrderConflictException e) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    problem.setTitle("Conflicting resubmission");
    problem.setProperty("existing_order_id", e.existingOrderId());
    return problem;
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
    String constraint = constraintName(e);
    UnknownReferenceException unknown =
        constraint == null ? null : FK_TO_UNKNOWN_REFERENCE.get(constraint);
    if (unknown != null) {
      return unknownReferenceProblem(unknown);
    }
    // The API validates everything the CHECKs enforce, so this is a bug; log only the name.
    LOG.error("Unexpected constraint violation: {}", constraint);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "The order could not be stored.");
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    FieldError error;
    if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      error = new FieldError(jsonPath(mapping.getPath()), "invalid_type", "has the wrong type");
    } else {
      error = new FieldError("body", "invalid_json", "must be a JSON object");
    }
    return ResponseEntity.badRequest()
        .body(
            problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request body could not be read.",
                List.of(error)));
  }

  private static ProblemDetail unknownReferenceProblem(UnknownReferenceException e) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Unknown reference",
        "The request refers to a tenant or patient that does not exist.",
        e.errors());
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, List<FieldError> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setProperty("errors", errors);
    return problem;
  }

  private static String constraintName(DataIntegrityViolationException e) {
    if (NestedExceptionUtils.getMostSpecificCause(e) instanceof PSQLException psql) {
      ServerErrorMessage message = psql.getServerErrorMessage();
      return message == null ? null : message.getConstraint();
    }
    return null;
  }

  /** Renders a Jackson path as {@code items[0].quantity}. */
  private static String jsonPath(List<JsonMappingException.Reference> path) {
    return path.stream()
        .map(
            ref ->
                ref.getFieldName() != null ? "." + ref.getFieldName() : "[" + ref.getIndex() + "]")
        .collect(Collectors.joining())
        .replaceFirst("^\\.", "");
  }
}
