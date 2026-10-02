package com.jacobian.orders.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jacobian.orders.PostgresTestConfiguration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The OpenAPI document documents the v1 order operations and every status they return. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class OpenApiDocsTests {

  @Autowired private TestRestTemplate rest;

  @Test
  void documentsTheV1OrderOperationsAndTheirResponseCodes() {
    ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    DocumentContext doc = JsonPath.parse(response.getBody());
    assertThat(doc.<String>read("$.info.version")).isEqualTo("v1");
    assertThat(doc.<Map<String, Object>>read("$.paths")).containsOnlyKeys("/v1/orders");
    assertThat(doc.<Map<String, Object>>read("$.paths['/v1/orders'].post.responses"))
        .containsOnlyKeys("200", "201", "400", "409", "422");
    assertThat(doc.<Map<String, Object>>read("$.paths['/v1/orders'].get.responses"))
        .containsOnlyKeys("200", "400", "422");
  }

  @Test
  void documentsSnakeCaseSchemasAndTheProblemShape() {
    DocumentContext doc = JsonPath.parse(rest.getForEntity("/v3/api-docs", String.class).getBody());

    assertThat(doc.<Map<String, Object>>read("$.components.schemas.OrderRequest.properties"))
        .containsKeys("tenant_id", "external_order_id", "submitted_at");
    assertThat(
            doc.<String>read(
                "$.paths['/v1/orders'].post.responses['409'].content['application/problem+json']"
                    + ".schema['$ref']"))
        .endsWith("/ConflictProblem");
    assertThat(doc.<Map<String, Object>>read("$.components.schemas.ConflictProblem.properties"))
        .containsKey("existing_order_id");
  }
}
