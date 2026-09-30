package com.jacobian.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres for tests, started with Testcontainers and wired into the datasource through {@link
 * ServiceConnection}. Keep the image tag in sync with {@code compose.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

  static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18");

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE);
  }
}
