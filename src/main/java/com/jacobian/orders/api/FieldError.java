package com.jacobian.orders.api;

/**
 * One problem with one request field, reported in the {@code errors} array of an RFC 9457 problem
 * response. {@code field} is a JSON path such as {@code items[0].quantity}, or a header or query
 * parameter name.
 */
public record FieldError(String field, String code, String message) {}
