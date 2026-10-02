package com.jacobian.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class DatabaseConnectionTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void executesQueryAgainstPostgres() {
    assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
        .startsWith("PostgreSQL 18");
  }

  @Test
  void appConnectsAsTheRowLevelSecurityBoundRole() {
    Map<String, Object> role =
        jdbcTemplate.queryForMap(
            "SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");

    assertThat(role)
        .containsEntry("rolname", "orders_app")
        .containsEntry("rolsuper", false)
        .containsEntry("rolbypassrls", false);
  }

  @Test
  void flywayCreatedTheServiceSchemaAsItsOwnerWithForcedRowLevelSecurity() {
    List<Map<String, Object>> tables =
        jdbcTemplate.queryForList(
            """
            SELECT c.relname, pg_get_userbyid(c.relowner) AS owner,
                   c.relrowsecurity, c.relforcerowsecurity
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'order_service' AND c.relkind = 'r'
            ORDER BY c.relname
            """);

    assertThat(tables)
        .extracting(t -> t.get("relname"))
        .containsExactly("flyway_schema_history", "order_items", "orders", "patients", "tenants");
    assertThat(tables).allSatisfy(t -> assertThat(t).containsEntry("owner", "orders_owner"));
    assertThat(tables)
        .filteredOn(t -> List.of("order_items", "orders", "patients").contains(t.get("relname")))
        .allSatisfy(
            t ->
                assertThat(t)
                    .containsEntry("relrowsecurity", true)
                    .containsEntry("relforcerowsecurity", true));
  }
}
