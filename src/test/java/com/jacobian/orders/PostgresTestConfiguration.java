package com.jacobian.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Real Postgres for tests, started with Testcontainers. Keep the image tag in sync with {@code
 * compose.yaml}.
 *
 * <p>The container runs {@code docker/postgres/01-roles.sql} on first start, like compose does. The
 * connection is wired explicitly rather than with {@code @ServiceConnection}, which would hand both
 * the app and Flyway the container's superuser and silently bypass row-level security: the app
 * connects as {@code orders_app}, and Flyway as the schema owner {@code orders_owner}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18");

  @Bean
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withCopyFileToContainer(
            MountableFile.forHostPath("docker/postgres/01-roles.sql"),
            "/docker-entrypoint-initdb.d/01-roles.sql");
  }

  @Bean
  DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer<?> postgres) {
    return registry -> {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", () -> "orders_app");
      registry.add("spring.datasource.password", () -> "orders_app");
      registry.add("spring.flyway.user", () -> "orders_owner");
      registry.add("spring.flyway.password", () -> "orders_owner");
      registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/seed");
    };
  }
}
