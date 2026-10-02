package com.jacobian.orders.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI metadata. springdoc serves the document at {@code /v3/api-docs} and Swagger UI at
 * {@code /swagger-ui/index.html}.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Order Intake API",
            version = "v1",
            description =
                """
                Multi-tenant order intake. `POST /v1/orders` accepts orders idempotently on \
                `(tenant_id, external_order_id)`; `GET /v1/orders` lists the calling tenant's \
                orders newest first with keyset pagination. JSON is snake_case, and errors are \
                RFC 9457 problem documents (`application/problem+json`) with field-level \
                problems in `errors`."""))
public class OpenApiConfiguration {

  /**
   * Builds schemas with the application's {@link ObjectMapper}, so documented property names follow
   * the configured snake_case naming strategy. OpenAPI 3.1 mode matches the document springdoc
   * serves, and honors {@code @Schema(types = ...)} for nullable fields.
   */
  @Bean
  ModelResolver openApiModelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper).openapi31(true);
  }
}
