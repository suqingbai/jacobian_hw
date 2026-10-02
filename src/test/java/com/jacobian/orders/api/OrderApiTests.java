package com.jacobian.orders.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jacobian.orders.PostgresTestConfiguration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end API tests against a real Postgres, with the app connected as {@code orders_app} so
 * row-level security is in force. Tenants and patients come from the seed in {@code db/seed}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class OrderApiTests {

  static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
  static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

  /** Exists in both tenants (as different people). */
  static final String SHARED_PATIENT_ID = "P-345678";

  /** Exists only in tenant B. */
  static final String TENANT_B_ONLY_PATIENT = "P-200001";

  @Autowired private TestRestTemplate rest;

  @Test
  void createsAnOrderAndReturns201WithTheStoredOrder() {
    Map<String, Object> order = validOrder(TENANT_A);
    order.put("external_order_id", "  " + order.get("external_order_id") + "  ");

    ResponseEntity<String> response = post(order);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DocumentContext body = JsonPath.parse(response.getBody());
    assertThat(UUID.fromString(body.read("$.id"))).isNotNull();
    assertThat(body.<String>read("$.tenant_id")).isEqualTo(TENANT_A);
    assertThat(body.<String>read("$.external_order_id"))
        .isEqualTo(order.get("external_order_id").toString().strip());
    assertThat(body.<String>read("$.status")).isEqualTo("submitted");
    assertThat(body.<String>read("$.order_type")).isEqualTo("lab");
    assertThat(body.<String>read("$.priority")).isEqualTo("routine");
    assertThat(body.<String>read("$.patient.patient_id")).isEqualTo(SHARED_PATIENT_ID);
    assertThat(body.<String>read("$.submitted_by.user_id")).isEqualTo("u-001");
    assertThat(body.<List<String>>read("$.items[*].code")).containsExactly("CBC", "BMP");
    assertThat(body.<String>read("$.submitted_at")).isEqualTo(order.get("submitted_at"));
  }

  @Test
  void anIdenticalResubmissionReturns200WithTheOriginalOrder() {
    Map<String, Object> order = validOrder(TENANT_A);

    String firstId = JsonPath.read(post(order).getBody(), "$.id");
    ResponseEntity<String> replay = post(order);

    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(replay.getBody(), "$.id")).isEqualTo(firstId);
  }

  @Test
  void aReplayIgnoresSubmittedAtSubmitterItemOrderAndPaddingAndKeepsTheOriginal() {
    Map<String, Object> order = validOrder(TENANT_A);
    String firstId = JsonPath.read(post(order).getBody(), "$.id");

    Map<String, Object> replay = new LinkedHashMap<>(order);
    replay.put("external_order_id", " " + order.get("external_order_id") + "\t");
    replay.put("submitted_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    replay.put("submitted_by", Map.of("user_id", "u-retry-bot", "display_name", "Retry Bot"));
    replay.put("items", reversed(items(order)));

    ResponseEntity<String> response = post(replay);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    DocumentContext body = JsonPath.parse(response.getBody());
    assertThat(body.<String>read("$.id")).isEqualTo(firstId);
    assertThat(body.<String>read("$.submitted_at")).isEqualTo(order.get("submitted_at"));
    assertThat(body.<String>read("$.submitted_by.user_id")).isEqualTo("u-001");
    assertThat(body.<List<String>>read("$.items[*].code")).containsExactly("CBC", "BMP");
  }

  @Test
  void aConflictingResubmissionReturns409NamingTheExistingOrder() {
    Map<String, Object> order = validOrder(TENANT_A);
    String firstId = JsonPath.read(post(order).getBody(), "$.id");

    Map<String, Object> changed = new LinkedHashMap<>(order);
    changed.put("priority", "stat");
    ResponseEntity<String> response = post(changed);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(JsonPath.<String>read(response.getBody(), "$.existing_order_id")).isEqualTo(firstId);
  }

  @Test
  void theSameExternalOrderIdIsIndependentPerTenant() {
    Map<String, Object> inA = validOrder(TENANT_A);
    Map<String, Object> inB = validOrder(TENANT_B);
    inB.put("external_order_id", inA.get("external_order_id"));

    assertThat(post(inA).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(post(inB).getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void anUnknownTenantReturns422() {
    ResponseEntity<String> response = post(validOrder(UUID.randomUUID().toString()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(errors(response)).containsExactly("tenant_id:unknown_tenant");
  }

  @Test
  void anUnknownPatientReturns422EvenIfAnotherTenantHasIt() {
    Map<String, Object> order = validOrder(TENANT_A);
    order.put("patient", Map.of("patient_id", TENANT_B_ONLY_PATIENT));

    ResponseEntity<String> response = post(order);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(errors(response)).containsExactly("patient.patient_id:unknown_patient");
  }

  @Test
  void anInvalidSubmissionReportsEveryFieldErrorAtOnce() {
    Map<String, Object> order = validOrder(TENANT_A);
    order.remove("external_order_id");
    order.put("order_type", "surgery");
    order.put("status", "accepted");
    order.put("items", List.of(Map.of("code", "CBC", "quantity", 0)));
    order.put("notes", "x".repeat(2001));
    order.put("submitted_at", Instant.now().plus(Duration.ofHours(25)).toString());

    ResponseEntity<String> response = post(order);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(errors(response))
        .containsExactlyInAnyOrder(
            "external_order_id:required",
            "order_type:invalid_value",
            "status:invalid_value",
            "items[0].quantity:too_small",
            "notes:too_long",
            "submitted_at:in_future");
  }

  @Test
  void requiredFieldsAndEmptyItemsAreReported() {
    ResponseEntity<String> response = post(Map.of("items", List.of()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response))
        .containsExactlyInAnyOrder(
            "tenant_id:required",
            "external_order_id:required",
            "submitted_by:required",
            "patient:required",
            "order_type:required",
            "priority:required",
            "items:required",
            "submitted_at:required");
  }

  @Test
  void lengthLimitsCountCharactersLikePostgres() {
    Map<String, Object> emojiNotes = validOrder(TENANT_A);
    emojiNotes.put("notes", "😀".repeat(2000));
    assertThat(post(emojiNotes).getStatusCode()).isEqualTo(HttpStatus.CREATED);

    Map<String, Object> tooLong = validOrder(TENANT_A);
    tooLong.put("external_order_id", "x".repeat(101));
    tooLong.put("submitted_by", Map.of("user_id", "u".repeat(101), "display_name", "d".repeat(51)));
    tooLong.put("patient", Map.of("patient_id", "p".repeat(101)));
    tooLong.put(
        "items",
        List.of(Map.of("code", "c".repeat(65), "description", "d".repeat(501), "quantity", 1)));
    ResponseEntity<String> response = post(tooLong);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(response))
        .containsExactlyInAnyOrder(
            "external_order_id:too_long",
            "submitted_by.user_id:too_long",
            "submitted_by.display_name:too_long",
            "patient.patient_id:too_long",
            "items[0].code:too_long",
            "items[0].description:too_long");
  }

  @Test
  void nulCharactersAndMalformedValuesAreRejected() {
    Map<String, Object> order = validOrder("not-a-uuid");
    order.put("notes", "before\u0000after");
    order.put("submitted_at", "2026-05-19T14:30:00");

    assertThat(errors(post(order)))
        .containsExactlyInAnyOrder(
            "tenant_id:invalid_format", "notes:invalid_character", "submitted_at:invalid_format");
  }

  @Test
  void wronglyTypedJsonIsReportedWithItsPath() {
    Map<String, Object> stringQuantity = validOrder(TENANT_A);
    stringQuantity.put("items", List.of(Map.of("code", "CBC", "quantity", "1")));
    assertThat(errors(post(stringQuantity))).containsExactly("items[0].quantity:invalid_type");

    Map<String, Object> fractionalQuantity = validOrder(TENANT_A);
    fractionalQuantity.put("items", List.of(Map.of("code", "CBC", "quantity", 1.5)));
    assertThat(errors(post(fractionalQuantity))).containsExactly("items[0].quantity:invalid_type");

    ResponseEntity<String> malformed = post("{\"tenant_id\": ");
    assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(errors(malformed)).containsExactly("body:invalid_json");
  }

  @Test
  void tenantBCannotSeeTenantAsOrders() {
    Instant windowStart = uniqueWindow();
    Map<String, Object> order = validOrder(TENANT_A);
    order.put("submitted_at", windowStart.plusSeconds(30).toString());
    String id = JsonPath.read(post(order).getBody(), "$.id");
    String window =
        "?submitted_from=" + windowStart + "&submitted_to=" + windowStart.plusSeconds(60);

    assertThat(orderIds(get(TENANT_A, window))).containsExactly(id);
    assertThat(orderIds(get(TENANT_B, window))).isEmpty();
  }

  @Test
  void ordersArePagedNewestFirstWithAKeysetCursor() {
    Instant windowStart = uniqueWindow();
    List<String> created = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Map<String, Object> order = validOrder(TENANT_B);
      order.put("patient", Map.of("patient_id", TENANT_B_ONLY_PATIENT));
      // Two orders share a timestamp, so the id tie-breaker is exercised too.
      order.put("submitted_at", windowStart.plusSeconds(Math.min(i, 3) * 10L).toString());
      created.add(JsonPath.read(post(order).getBody(), "$.id"));
    }
    String window =
        "submitted_from=" + windowStart + "&submitted_to=" + windowStart.plusSeconds(60);

    List<String> seen = new ArrayList<>();
    List<Integer> pageSizes = new ArrayList<>();
    String cursor = null;
    do {
      String query = "?" + window + "&limit=2" + (cursor == null ? "" : "&cursor=" + cursor);
      ResponseEntity<String> page = get(TENANT_B, query);
      assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
      List<String> ids = orderIds(page);
      pageSizes.add(ids.size());
      seen.addAll(ids);
      cursor = JsonPath.read(page.getBody(), "$.next_cursor");
    } while (cursor != null);

    assertThat(pageSizes).containsExactly(2, 2, 1);
    assertThat(seen).containsExactlyInAnyOrderElementsOf(created).doesNotHaveDuplicates();
    assertThat(seen.get(0)).isEqualTo(created.get(4));
  }

  @Test
  void listingValidatesTheTenantHeaderAndParameters() {
    assertThat(errors(get(null, ""))).containsExactly("X-Tenant-Id:required");
    assertThat(errors(get("nope", ""))).containsExactly("X-Tenant-Id:invalid_format");
    assertThat(errors(get(TENANT_A, "?limit=0"))).containsExactly("limit:out_of_range");
    assertThat(errors(get(TENANT_A, "?limit=101"))).containsExactly("limit:out_of_range");
    assertThat(errors(get(TENANT_A, "?cursor=garbage"))).containsExactly("cursor:invalid_value");
    assertThat(errors(get(TENANT_A, "?submitted_from=yesterday")))
        .containsExactly("submitted_from:invalid_format");

    ResponseEntity<String> unknown = get(UUID.randomUUID().toString(), "");
    assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(errors(unknown)).containsExactly("X-Tenant-Id:unknown_tenant");
  }

  static Map<String, Object> validOrder(String tenantId) {
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("tenant_id", tenantId);
    order.put("external_order_id", "PO-" + UUID.randomUUID());
    order.put("submitted_by", Map.of("user_id", "u-001", "display_name", "Jane Doe"));
    order.put("patient", Map.of("patient_id", SHARED_PATIENT_ID));
    order.put("order_type", "lab");
    order.put("priority", "routine");
    order.put("status", "submitted");
    order.put(
        "items",
        List.of(
            Map.of("code", "CBC", "description", "Complete Blood Count", "quantity", 1),
            Map.of("code", "BMP", "quantity", 2)));
    order.put("notes", "Pre-op screening");
    order.put(
        "submitted_at",
        Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS).toString());
    return order;
  }

  private ResponseEntity<String> post(Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.exchange("/orders", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
  }

  private ResponseEntity<String> get(String tenantId, String query) {
    HttpHeaders headers = new HttpHeaders();
    if (tenantId != null) {
      headers.set("X-Tenant-Id", tenantId);
    }
    return rest.exchange(
        "/orders" + query, HttpMethod.GET, new HttpEntity<>(headers), String.class);
  }

  private static List<String> orderIds(ResponseEntity<String> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return JsonPath.read(response.getBody(), "$.orders[*].id");
  }

  private static List<String> errors(ResponseEntity<String> response) {
    List<Map<String, String>> errors = JsonPath.read(response.getBody(), "$.errors");
    return errors.stream().map(e -> e.get("field") + ":" + e.get("code")).toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> items(Map<String, Object> order) {
    return (List<Object>) order.get("items");
  }

  private static List<Object> reversed(List<Object> list) {
    List<Object> copy = new ArrayList<>(list);
    java.util.Collections.reverse(copy);
    return copy;
  }

  /** A one-minute window in the past that no other test uses. */
  private static Instant uniqueWindow() {
    long minutes = ThreadLocalRandom.current().nextLong(0, 5_000_000);
    return Instant.parse("2010-01-01T00:00:00Z").plus(Duration.ofMinutes(minutes));
  }
}
