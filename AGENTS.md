# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Build/lint/test/run commands: see `README.md`. `./gradlew build` must stay green; run `./gradlew spotlessApply` before committing Java changes.
- All dependency and plugin versions live in `gradle/libs.versions.toml`; build scripts reference catalog aliases only. Spring Boot starters are versionless and resolved through the `spring-boot-dependencies` platform; Flyway is the one deliberate override (see the comment in the catalog), so drop it once Boot manages a version that supports PostgreSQL 18.
- Stay on Spring Boot 3.x (not 4.x) and Gradle 8.x: Spring Boot 3.5 docs list Gradle 7.6.4+/8.4+ as supported, not 9.x.
- Tests and the `dev` profile need Docker: tests use a Testcontainers Postgres (`PostgresTestConfiguration`, no H2), dev uses Spring Boot Docker Compose support with `compose.yaml`. Keep the Postgres image tag identical in both.
- The app must connect as `orders_app` and Flyway as `orders_owner` (roles from `docker/postgres/01-roles.sql`); never use `@ServiceConnection` or the Compose service connection for Postgres, they force the superuser and silently disable row-level security (see `docs/adr/ADR-001-tenant-isolation-rls.md`). Tenant-scoped DB work must run in a transaction that calls `TenantSession.bind` first.
- Flyway owns the schema (`ddl-auto=validate`); schema changes go in a new `db/migration` file, never in an edited applied migration.
- API endpoints live under `/v1`; their OpenAPI docs are springdoc annotations on `OrderController` and the `api` DTOs. When a status, field, or validation rule changes, update the `@ApiResponse`/`@Schema` annotations in the same change (`OpenApiDocsTests` checks the documented statuses).
- The container image is built by Jib (`jib {}` in `build.gradle.kts`, usage in README). Keep `configurationName = "productionRuntimeClasspath"`: `runtimeClasspath` also carries `developmentOnly` Docker Compose support. CI only runs `jibBuildTar`, never a push. The `app` service in `compose.yaml` must stay behind a Compose profile so the `dev` profile's Docker Compose support starts only Postgres.
- Checkstyle (`config/checkstyle/checkstyle.xml`) holds static rules only; formatting belongs to google-java-format via Spotless, so do not add formatting rules to Checkstyle.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.
