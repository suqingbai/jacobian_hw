package com.jacobian.orders.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One problem with one request field, reported in the {@code errors} array of an RFC 9457 problem
 * response. {@code field} is a JSON path such as {@code items[0].quantity}, or a header or query
 * parameter name.
 */
@Schema(description = "One problem with one request field.")
public record FieldError(
    @Schema(
            description = "JSON path of the body field, or the header or query parameter name.",
            example = "items[0].quantity")
        String field,
    @Schema(
            description =
                "Machine-readable problem code, such as required, invalid_format, invalid_value,"
                    + " invalid_type, invalid_json, invalid_character, too_long, too_small,"
                    + " out_of_range, in_future, unknown_tenant, or unknown_patient.",
            example = "too_small")
        String code,
    @Schema(description = "Human-readable explanation.", example = "must be at least 1")
        String message) {}
