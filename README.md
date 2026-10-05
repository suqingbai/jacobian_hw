# Order Intake Service

[![CI](https://github.com/suqingbai/jacobian_hw/actions/workflows/ci.yml/badge.svg)](https://github.com/suqingbai/jacobian_hw/actions/workflows/ci.yml)

Multi-tenant order intake service.

## Stack

- Java 21 (Gradle toolchain)
- Spring Boot 3.5 (web, actuator, validation, data JPA)
- springdoc-openapi 2.x for the OpenAPI document and Swagger UI
- PostgreSQL 18, with Flyway owning the schema (`src/main/resources/db/migration`); Hibernate runs
  with `ddl-auto=validate` and never creates or alters tables
- Gradle 8 with the Kotlin DSL; all versions live in `gradle/libs.versions.toml`
- Lint: Spotless + google-java-format (formatting) and Checkstyle (static rules,
  `config/checkstyle/checkstyle.xml`)

## quick start

```sh
docker compose up  # start the dev Postgres and the app container
health check: http://localhost:8080/actuator/health
swagger UI: http://localhost:8080/swagger-ui/index.html
```
**Seed data**:

tenant: `11111111-1111-1111-1111-111111111111`
patient: `P-345678`, `P-100001`

tenant `22222222-2222-2222-2222-222222222222`
Patient: `P-345678`, `P-200001`

**Create order** and **list orders** via Swagger UI '**try it out**' buttons, or `curl` as in the [API](#api) section.


## Database

Choose PostgreSQL for its maturity, low cost(open source), and rich features for future proofing(full text search, vector database support)

Created `order_service` schema for the service to maintain modularity of the database layer.

Assumed `tenants` and `patients` are maintained outside this service, and the intake service never creates or update them. `patient` and `tenant` tables are the local copy of the required data. Other service(s) maintains the source of truth.

Separate database roles for the service, flyway, and admin-user.

- **Schema:** in the `order_service` schema:
  - `tenants` and `patients`: read-only reference data.
  - `orders` and `order_items`: owned by the service.
- **Roles:** four database identities, each with one job. Only the bootstrap superuser is a
  superuser, and nothing connects as it after setup.

  | Role | Used by | Notes |
    |---|---|---|
  | `POSTGRES_USER` | the container, once | runs [`docker/postgres/01-roles.sql`](docker/postgres/01-roles.sql), which creates the three roles below |
  | `orders_owner` | Flyway (`spring.flyway.user`) | owns the schema and every table |
  | `orders_app` | the service (`spring.datasource.username`) | always under row-level security; no DDL |
  | `orders_admin_user` | people debugging or supporting the service | `BYPASSRLS`; reads and writes every tenant's rows |

  Don't use Boot's `@ServiceConnection`, or the Compose service connection, for Postgres.
  They hand the app the superuser and silently bypass row-level security. See ADR-001.
- **Dev profile:**
  - `bootRun` with the `dev` profile starts the `postgres` service of
    [`compose.yaml`](compose.yaml) through Spring Boot's Docker Compose support
    (`spring.docker.compose.start.arguments` limits it to that service, since the app runs
    locally). There is no separate `docker compose up` step.
  - It connects to `localhost:${ORDERS_DB_PORT:-5432}` as the roles above, and it also loads
    the dev reference data in `db/seed`: tenants `1111...` and `2222...`, and patient
    `P-345678` in each.
  - The container is stopped (not removed) when the app exits, so data survives restarts.
    `docker compose down -v` resets it; without `-v`, the Postgres data volume is left behind.
  - The roles are created only when the data volume is first initialized, so a volume from
    before this change needs `docker compose down -v` once.
- **Tests:** the Spring Boot tests import `PostgresTestConfiguration`. It starts a
  Testcontainers Postgres (same image tag as `compose.yaml`), runs the same `01-roles.sql`, and
  wires the same users and the seed explicitly. There is no in-memory database substitute, so
  `./gradlew test` and `./gradlew build` need Docker.
- **Other profiles:** Compose support is off. Supply the connection through
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` (`orders_app`), and
  `SPRING_DATASOURCE_PASSWORD`, plus `SPRING_FLYWAY_USER` (`orders_owner`) and
  `SPRING_FLYWAY_PASSWORD`.
- **Schema changes:** add a new versioned Flyway migration (`V<n>__<description>.sql`) under
  `src/main/resources/db/migration`; never edit an applied one.

## Development

Requires a JDK 21 on the machine (or let Gradle toolchain resolution find one) and a running Docker
daemon: both the dev profile and the tests start PostgreSQL in a container. Use the wrapper:

```sh
./gradlew build            # compile, lint, and test (fails on any lint violation)
./gradlew check            # lint + tests only
./gradlew spotlessApply    # auto-fix formatting
./gradlew test             # tests only
./gradlew bootRun --args='--spring.profiles.active=dev'   # run on http://localhost:8080 with postgreSQL container running and seeded patient and tenant records
```

CI (`.github/workflows/ci.yml`) runs `./gradlew build` and `./gradlew jibBuildTar` (to prove the
container image builds) on every pull request and push to `main`. Pushes to `main` then publish
the image (see [Container image](#container-image)); pull requests never publish.

Health check once the app is running:

```sh
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},...}}
```

## Container image

[Jib](https://github.com/GoogleContainerTools/jib) builds the image straight from the Gradle build,
with no Dockerfile. The image is
[`suebai/order-intake`](https://hub.docker.com/r/suebai/order-intake) on Docker Hub, tagged with
the project version and `latest`.

```sh
./gradlew jibDockerBuild                       # to the local Docker daemon
./gradlew jibDockerBuild -Pimage=my/name:tag   # any other name (only that tag)
./gradlew jibBuildTar                          # build/jib-image.tar, no Docker daemon needed
./gradlew jib                                  # build and push to Docker Hub
```

- **Pushing:** `./gradlew jib` uses your Docker login (`docker login` as an account that can push
  to `suebai/order-intake`); it does not need a local Docker daemon.
- **CI publishing:** every push to `main` publishes automatically once build and tests pass: CI
  logs in as `suebai` with the `DOCKER_HUB_TOKEN` secret and runs `./gradlew jib`, tagging the
  project version, `latest`, and the 7-character commit SHA. Pull request runs only run
  `jibBuildTar`.
- **Base:** `gcr.io/distroless/java21-debian13:nonroot`, pinned by digest in `build.gradle.kts`.
  JRE only, no shell or package manager.
- **Container:** runs as uid/gid `65532` (non-root), exposes `8080`, and caps the heap at 75% of
  the container memory limit (`-XX:MaxRAMPercentage=75`).
- **Classpath:** the same as `bootJar` (`productionRuntimeClasspath`), so the `developmentOnly`
  Docker Compose support is not in the image.
- **Platform:** `linux/amd64` by default; build for another one with
  `-Djib.from.platforms=linux/arm64`.

The image runs the default profile, so it needs the connection from the environment (see
[Database](#database) for the roles):

| Variable | Value |
|---|---|
| `SPRING_DATASOURCE_URL` | e.g. `jdbc:postgresql://postgres:5432/orders` |
| `SPRING_DATASOURCE_USERNAME` | `orders_app` (the runtime role, under row-level security) |
| `SPRING_DATASOURCE_PASSWORD` | its password |
| `SPRING_FLYWAY_USER` | `orders_owner` (runs the migrations on startup) |
| `SPRING_FLYWAY_PASSWORD` | its password |

### Full stack with Docker Compose

`docker compose up` starts the dev Postgres and the `app` service, which runs
`suebai/order-intake:latest` with the variables above set for it:

```sh
./gradlew jibDockerBuild   # or skip it to pull the published image
docker compose up -d
curl http://localhost:8080/actuator/health
docker compose down -v     # stop and delete the data
```

- **Either this or `bootRun`:** both serve on port 8080, so run the full stack or the dev
  profile's `bootRun`, not both at once.
- **Ordering:** the app starts once Postgres is healthy. It has no container healthcheck (the
  distroless image has no shell or curl), so poll `/actuator/health` until it is UP.
- **Seed data:** it sets `SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed`, so
  the dev tenants and patients are loaded and the [`POST /v1/orders`](#post-v1orders) sample
  works. That is for local use only; the image itself loads no seed data.
- **Port:** published on `${ORDERS_APP_PORT:-8080}`.

## API

The API is versioned in the path: every endpoint lives under `/v1`. Actuator endpoints stay
unversioned under `/actuator`.

With the app running, from `bootRun` or the container, the OpenAPI document and Swagger UI are
at (enabled explicitly in every profile):

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

All JSON is snake_case. Errors are RFC 9457 problem documents (`application/problem+json`).
Field-level problems are listed in `errors` as `{field, code, message}`.

### `POST /v1/orders`

```sh
curl -s localhost:8080/v1/orders -H 'Content-Type: application/json' -d '{
  "tenant_id": "11111111-1111-1111-1111-111111111111",
  "external_order_id": "PO-2026-001",
  "submitted_by": { "user_id": "u-001", "display_name": "Jane Doe" },
  "patient": { "patient_id": "P-345678" },
  "order_type": "lab",
  "priority": "routine",
  "status": "submitted",
  "items": [ { "code": "CBC", "description": "Complete Blood Count", "quantity": 1 } ],
  "notes": "Pre-op screening",
  "submitted_at": "2026-05-19T14:30:00.000Z"
}'
```

| Status | When | Body |
|---|---|---|
| `201 Created` | a new `(tenant_id, external_order_id)` | the stored order, including its `id` |
| `200 OK` | a replay: same `external_order_id`, same content | the original order (original `id`, `submitted_at`, submitter) |
| `409 Conflict` | same `external_order_id`, different content | problem with `existing_order_id` |
| `422 Unprocessable Content` | the tenant or the patient does not exist | problem, `errors[].code` = `unknown_tenant` / `unknown_patient` |
| `400 Bad Request` | invalid or malformed input; every problem is reported at once | problem with `errors` |

What counts as "the same content" is defined in [ADR-002](docs/adr/ADR-002-idempotency.md).
In short, `submitted_at`, `submitted_by`, and item order are ignored.

Where this deviates from, or fills gaps in, the canonical schema in the assignment:

- **Patient:** the request identifies the patient only by `patient.patient_id`, the internal
  id the client looks up first. Patients and tenants are reference data maintained outside
  this service, so it never creates or updates them. Other patient fields, such as the
  sample's `name`, are ignored, as are unknown fields in general.
- **Ids:** `external_order_id`, `submitted_by.user_id`, and `patient.patient_id` are trimmed,
  then compared exactly (case-sensitive). They are at most 100 characters.
- **Lengths:** `display_name` is at most 50 characters, item `code` 64, item `description`
  500, and `notes` 2000. Lengths count Unicode characters (code points), as Postgres does.
  NUL characters are rejected.
- **Values:** `items` needs at least one entry, and each `quantity` must be an integer of at
  least 1. `"1"` and `1.5` are type errors. `status` may be omitted; if present, it must be
  `submitted`.
- **`submitted_at`:** an ISO-8601 date-time with an offset, at most 24 hours in the future,
  stored to microsecond precision.

### `GET /v1/orders`

```sh
curl -s 'localhost:8080/v1/orders?submitted_from=2026-05-01T00:00:00Z&submitted_to=2026-06-01T00:00:00Z&limit=20' \
  -H 'X-Tenant-Id: 11111111-1111-1111-1111-111111111111'
# {"orders":[{...}, ...], "next_cursor":"MjAyNi0w..."}
```

- **Tenant:** the `X-Tenant-Id` header is required. It is unauthenticated for now; in
  production the tenant comes from a JWT claim, and the tenant id never appears in a URL. An
  unknown tenant returns 422.
- **Filter:** `submitted_from` (inclusive) and `submitted_to` (exclusive) take ISO-8601
  date-times with an offset. Use `Z`, or URL-encode a `+` offset.
- **Paging:**
  - Results come newest first by `submitted_at`, then `id`.
  - `limit` defaults to 50 and allows 1-100.
  - Pass `next_cursor` back as `cursor` for the next page; it is `null` on the last page.
  - This is keyset pagination, so pages stay stable while new orders arrive.
