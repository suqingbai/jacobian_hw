package com.jacobian.orders;

import static org.assertj.core.api.Assertions.assertThat;

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
  void flywayAppliedBaselineMigration() {
    Boolean applied =
        jdbcTemplate.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

    assertThat(applied).isTrue();
  }
}
