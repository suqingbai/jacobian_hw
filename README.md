# Order Intake Service

[![CI](https://github.com/suqingbai/jacobian_hw/actions/workflows/ci.yml/badge.svg)](https://github.com/suqingbai/jacobian_hw/actions/workflows/ci.yml)

Multi-tenant order intake service for the take-home described in
[SeniorPlatformTakehome.md](SeniorPlatformTakehome.md). This is currently the starter skeleton:
a Spring Boot app with the actuator health endpoint, a PostgreSQL persistence layer, lint, and
tests wired up. The order
intake and query APIs are not implemented yet.

## Stack

- Java 21 (Gradle toolchain)
- Spring Boot 3.5 (web, actuator, validation, data JPA)
- PostgreSQL 18, with Flyway owning the schema (`src/main/resources/db/migration`); Hibernate runs
  with `ddl-auto=validate` and never creates or alters tables
- Gradle 8 with the Kotlin DSL; all versions live in `gradle/libs.versions.toml`
- Lint: Spotless + google-java-format (formatting) and Checkstyle (static rules,
  `config/checkstyle/checkstyle.xml`)

## Development

Requires a JDK 21 on the machine (or let Gradle toolchain resolution find one) and a running Docker
daemon: both the dev profile and the tests start PostgreSQL in a container. Use the wrapper:

```sh
./gradlew build            # compile, lint, and test (fails on any lint violation)
./gradlew check            # lint + tests only
./gradlew spotlessApply    # auto-fix formatting
./gradlew test             # tests only
./gradlew bootRun --args='--spring.profiles.active=dev'   # run on http://localhost:8080
```

### Database

- **Dev profile:** `bootRun` with the `dev` profile uses Spring Boot's Docker Compose support to
  start the Postgres service in [`compose.yaml`](compose.yaml) and point the datasource at it
  automatically; there is no separate `docker compose up` step. The container is stopped (not
  removed) when the app exits, so data survives restarts; `docker compose down` resets it.
- **Tests:** the Spring Boot tests import `PostgresTestConfiguration`, which starts a
  Testcontainers Postgres (same image tag as `compose.yaml`) wired in through `@ServiceConnection`.
  There is no in-memory database substitute, so `./gradlew test` and `./gradlew build` need Docker.
- **Other profiles:** Compose support is off, so supply the connection through the standard
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`
  environment variables.
- **Schema changes:** add a new versioned Flyway migration (`V<n>__<description>.sql`) under
  `src/main/resources/db/migration`; never edit an applied one.

CI (`.github/workflows/ci.yml`) runs `./gradlew build` on every pull request and push to `main`.

Health check once the app is running:

```sh
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},...}}
```
