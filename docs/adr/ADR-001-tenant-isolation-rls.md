# ADR-001: Tenant isolation with PostgreSQL row-level security

Status: accepted

## Context

Every request belongs to one tenant, and the assignment requires that tenant A's orders are not
visible to tenant B "under any code path". Filtering in application code meets that only as long
as every query, present and future, remembers the filter: one missed `WHERE tenant_id = ?` leaks
data, and no test can prove a negative across all code paths.

## Decision

Isolation is layered so the database refuses cross-tenant access even when the code forgets.

1. **`tenant_id` on every tenant-owned table**: `patients`, `orders`, `order_items`. The service
   also filters by it explicitly.
2. **Composite foreign keys.** `orders (tenant_id, patient_id) -> patients (tenant_id, id)` and
   `order_items (tenant_id, order_id) -> orders (tenant_id, id)`. A row can never reference
   another tenant's row.
3. **Row-level security with `FORCE`** on those three tables (`V3__tenant_rls.sql`).
   - The policy compares `tenant_id` with the transaction setting `app.tenant_id`.
   - The service binds each transaction first (`TenantSession`: `set_config('app.tenant_id', ?,
     true)`).
   - The setting is transaction-local, so it never leaks to the next user of a pooled
     connection.
   - An unbound transaction sees and writes nothing (`NULLIF(..., '')` makes the reset value
     fail closed).
4. **Database roles that make RLS real** (`docker/postgres/01-roles.sql`). Superusers, and
   roles with `BYPASSRLS`, ignore RLS. So:
   - **The app** connects as `orders_app`: no superuser, no `BYPASSRLS`, owns nothing. It can
     only `SELECT` the reference tables `tenants` and `patients`.
   - **Flyway** runs as the non-superuser schema owner `orders_owner`.
   - **People** debugging or supporting the service use `orders_admin_user`, which has
     `BYPASSRLS` and full DML but no DDL, and cannot write `flyway_schema_history`.
   - **The container superuser** only runs `01-roles.sql`.
   - All tables live in the `order_service` schema.

Tenant identification is deliberately simple for now, because security is out of scope:

- `POST /v1/orders` takes `tenant_id` from the JSON body.
- `GET /v1/orders` takes it from the unauthenticated `X-Tenant-Id` header, never from the URL.

In production, the tenant must come from a verified JWT claim.

## Alternatives considered

- **Filtering in the app only.** Simplest, but the guarantee depends on every query being
  written correctly, which is the failure mode the requirement is about.
- **Schema-per-tenant or database-per-tenant.** Strong isolation, but migrations and connection
  pools multiply with the number of tenants. That's heavy for a service whose tenants share one
  table shape.
- **Hibernate filters or a tenant-discriminator `@TenantId`.** These are still application-side:
  native queries and JDBC bypass them.

## Consequences

- **Verified by the tests:**
  - The app really connects as `orders_app`.
  - A query without a tenant filter sees only the bound tenant's rows.
  - Writing another tenant's row is refused with "row-level security".
  - The app cannot write reference data.
  - Over HTTP, tenant B cannot list tenant A's orders.
- **Every tenant-scoped operation must run in a transaction that binds the tenant first.**
  `TenantSession.bind` throws outside a transaction.
- **Spring Boot's `@ServiceConnection` (Testcontainers) and the Docker Compose service
  connection derive credentials from `POSTGRES_USER`, a superuser.** They ignore
  `spring.datasource.username`, which silently disables RLS. Tests and the dev profile wire
  the users explicitly instead, and a test asserts `current_user = orders_app`.
- **`FORCE` also binds the schema owner,** so a future data migration on a tenant table must
  set `app.tenant_id`. The dev/test seed (`db/seed`) does this.
- **`orders_admin_user` reads and changes PHI across tenants.** In production its sessions
  should be audited (e.g. pgaudit) and its credentials tightly held.
