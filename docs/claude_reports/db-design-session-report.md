# jacobian_hw order intake - proposed PostgreSQL schema

Status: **APPROVED by the captain on 2026-10-02 (final design = round 8); review concluded** (review page: `schema-review.html`, served by Lavish at http://127.0.0.1:4387/session/86abbfc6d0571367).
Nothing was added to or committed in the repo. Every worktree prototype was deleted afterwards, and `git status` is clean.

## What I did

1. Read `../../SeniorPlatformTakehome.md` (the assignment), the persistence setup on `main`/PR #3 (`V1__baseline.sql` is comment-only; `ddl-auto: validate`; `compose.yaml` uses `postgres:18` with superuser `POSTGRES_USER=orders`), and the version catalog (Spring Boot 3.5.16, Flyway 11.20.3).
2. Derived the requirements that shape the schema:
   - `POST /orders` returns 201 with the order id.
   - `GET /orders` is tenant-scoped with at least one filter.
   - Tenant A's orders must not be visible to tenant B "under any code path".
   - Idempotent submission, with `external_order_id` "unique per tenant".
   - `notes` is limited to 2000 characters, `quantity >= 1`, and `submitted_at` may be at most 24h in the future.
   - The only status is `submitted`. There are no scale or retention figures.
3. For each review round, I re-verified the DDL on a real `postgres:18` (18.6) container and re-prototyped the JPA mapping in the worktree under `ddl-auto=validate`, then deleted the prototype. The checks covered idempotency, RLS, grants, the composite FKs, and the CHECKs. Rounds 0 and 4 also ran EXPLAIN ANALYZE with 200k+ orders. After each round I re-served the Lavish page with revision marks.

## Captain feedback, verbatim

**Round 1 (freeform):**

> 1. drop tenant_api_key table, security is out of scope for now
> 2. change type of order_type column of orders from text to char(1), choose meaningful char as much as you can
> 3. create patients table with id, first_name, last_name columns, and change patient of orders to reference patients table by id, drop patient_name column
> 4. drop version column of orders, take the feedback and update the schema design, bring the new design back to Lavish for review. Don't implement it for now

**Round 0 form answers** (delivered with round 1):

- Old Q1: "API key per tenant", with the note "security is out fo scope for now,".
- Q3: "submitted_at range (submitted_from/submitted_to) with keyset pagination", with the note "this is out scope of db design".
- Old Q4: plaintext, with the note "plaintext".

**Round 2:**

- On lengths: "reduce ids to 100, display to 50, first and last to 20, tenant name to 100"
- On id comparison: "compare after trimming"
- On id comparison: "let's assume patient id is internal patient id, not external"
- On patient names: "patient name should be break to first and last, should not be nullable"
- Q2: "Yes: ship V3 RLS (patients, orders, order_items), app role split, and make tenant isolation the ADR"
- Q4: "Change the API: patient.first_name and patient.last_name replace patient.name"
- Q5: "Also priority: char(1) R (routine), U (urgent), S (stat); status stays text"

**Round 3:**

- "Don't do upsert on patient data. The assumption is patient data is maintained outside order intake service. Before submit order, the system should search patient to obtain patient_id. If submitted patient name is different than patient on record, let me think what should be done. For now, don't do anything with it."
- "make sure ADR about itempotency cover what the hash covers"
- "let's drop the patient first_name and last_name from request"
- "let's not add submitted_at into hash so the de-dup logic is robuster regardless how external system set submitted_at field in teh replay case."
- On the `tenant_id` source: "the source should be in the json request payload, not the http header"
- On the first-name length: "bump length to 30"
- "drop this upsert behavior"
- "for order_items table, let's not use composite primary key, add a new uuid pk column"
- "don't upsert tenants table, it should behavior like patient, the order intake service won't create or update new tenant record, same as patient"
- On `order_items_pkey (order_id, line_no)`: "drop this index for now, out of scope"
- Q6: "Reject with 422 when the patient is unknown; patients are registered elsewhere"
- Overall: "Revise: see my annotations and note"

**Round 4:**

- "For create, tenant_id read from json request. For GET, for now, let's put tenant id in http header. I don't feel comfortable to put tenant_id which is db primary key in url. even http header is temp solution, production solution, tenant id should be part of jwt token claims"
- On the hash item row: "don't include in order in hash key"
- On the not-added index: "add (tenant_id, order_id) index back"
- On A4: "for GET, put tenant_id in http header for now"
- Overall: "Revise: see my annotations and note"

**Round 5:**

- On the hash's `submitted_by` row: "drop submitted by and display name from hash"
- Overall: "Revise: see my annotations and note"

**Round 6:**

- On the `status` CHECK: "drop this constrains"
- On D1 (app-generated order id): "reminder me why we can't use database generated id here"
- On D8: "It was my mistake to drop version column. Let's put version column back"
- Overall: "Revise". Note: "two more feedback: 1. let's create all the tables under order_service schema, this is to provide modularity to the service at db level. 2. add a new \"orders_admin_user\" role and allow it by pass RLS and see all data for debugging and other proper usage"

**Round 7:**

- On `01-roles.sql`: "clarify, while flyway use orders_app role or orders role?"
- Overall: "Revise". Note: "help clarify which db user flyway is running as"

**Round 8:**

- On V3: "Give admin user write access too"

**Verdict (2026-10-02):** "Overall schema verdict: Approve the round 6 schema". The form label was stale; the page shown when the captain approved already included rounds 7 and 8, so the approved design is round 8 as documented here.

## Who connects as what (rounds 7-8)

| Role | Used by | Attributes | Can do | Spring setting |
|---|---|---|---|---|
| `POSTGRES_USER` (`orders` in compose, `test` in Testcontainers) | The container entrypoint, once | SUPERUSER | Runs `01-roles.sql`: creates the three roles below and grants `orders_owner` `CREATE` on the database. Nothing else connects as it. | none |
| **`orders_owner`** (new in round 7) | **Flyway** | LOGIN, NOSUPERUSER, NOBYPASSRLS | Creates `order_service` and owns every table there, including `flyway_schema_history`. Runs V1-V3, including the GRANTs. `FORCE` RLS applies to it, so a future data migration on tenant tables must set `app.tenant_id`. | `spring.flyway.user`/`password` |
| **`orders_app`** | **The service at runtime** | LOGIN, NOSUPERUSER, NOBYPASSRLS | DML on `orders` and `order_items`, `SELECT` on tenants and patients, always under RLS. No DDL. | `spring.datasource.username`/`password` |
| **`orders_admin_user`** | People (debugging, support, data fixes) | LOGIN, NOSUPERUSER, BYPASSRLS | Round 8: `SELECT` on everything; `INSERT`/`UPDATE`/`DELETE` on the four service tables across tenants (default privileges cover future tables). No writes to `flyway_schema_history`, no DDL. FKs and CHECKs still apply. | none |

**Trap found while answering:** Spring Boot's `@ServiceConnection` (used today in `PostgresTestConfiguration`) ignores `spring.datasource.username`. In the test, the app connected as the container superuser `test`, so RLS was silently bypassed. The fix is to drop `@ServiceConnection` and register the URL and users with a `DynamicPropertyRegistrar` (verified). Boot's Docker Compose support has the same issue for the dev profile, because it derives the connection from `POSTGRES_USER`: ignore the service with the `org.springframework.boot.ignore` label and set the datasource and Flyway users in `application-dev.yml`. Not yet verified; it belongs to the implementation task.

## Approved schema (round 8)

All tables are in the **`order_service`** schema.

| Table | Owner | Key points |
|---|---|---|
| `tenants` | **Reference data, read-only to the service** | `id uuid PK`, `name` (1-100), `created_at`. An unknown `tenant_id` returns 422. |
| `patients` | **Reference data, read-only to the service** | `PRIMARY KEY (tenant_id, id)`. `id text` is the internal patient id the client looks up first (1-100, trimmed). `first_name`/`last_name text NOT NULL` (1-30). PHI comments. An unknown patient returns 422. |
| `orders` | Service | **`id uuid PK DEFAULT uuidv7()`, generated by the DB** (JPA `@GeneratedValue(IDENTITY)`); `tenant_id`; `UNIQUE (tenant_id, external_order_id)`; `UNIQUE (tenant_id, id)`; `request_hash bytea` (32 B); `patient_id text` + `FK (tenant_id, patient_id) -> patients`; `order_type char(1)` L/I/M/C; `priority char(1)` R/U/S; **`status text NOT NULL DEFAULT 'submitted'`, no CHECK**; `submitted_by_user_id` (1-100, trimmed); `submitted_by_display_name` (<= 50); `notes` (<= 2000); `submitted_at`/`created_at`/`updated_at timestamptz`; **`version bigint NOT NULL DEFAULT 0`**; `CHECK submitted_at <= created_at + 24h`. |
| `order_items` | Service | `id uuid PK DEFAULT uuidv7()`; `tenant_id`, `order_id` with `FK (tenant_id, order_id) -> orders (tenant_id, id) ON DELETE CASCADE`; `line_no` (>= 1, ordering only); `code` (1-64); `description` (<= 500); `quantity >= 1`; index `(tenant_id, order_id)`. |

Roles: see "Who connects as what" above. They are cluster-wide, created by `01-roles.sql` before Flyway runs: compose `docker-entrypoint-initdb.d`, and Testcontainers `withInitScript`.

- `orders_app`: `LOGIN NOSUPERUSER NOBYPASSRLS`, the service's runtime connection.
  - `SELECT, INSERT, UPDATE` on `orders` and `order_items`.
  - `SELECT` on `tenants` and `patients`.
  - Subject to RLS.
- **`orders_admin_user`**: `LOGIN NOSUPERUSER BYPASSRLS`.
  - `USAGE` on the schema.
  - `SELECT` on all tables.
  - **`INSERT`/`UPDATE`/`DELETE` on `tenants`, `patients`, `orders`, `order_items`** (round 8), granted by name so `flyway_schema_history` stays read-only to it.
  - `ALTER DEFAULT PRIVILEGES` giving full DML on future tables.
- `orders_owner`: Flyway's non-superuser schema owner.

RLS: `patients`, `orders`, and `order_items` have it, with `FORCE`. The tenant is set per transaction with `set_config('app.tenant_id', ?, true)`.

Spring settings:

- `spring.flyway.schemas=order_service`: Flyway creates the schema and keeps `flyway_schema_history` in it.
- `spring.flyway.placeholders.appRole=orders_app` and `adminRole=orders_admin_user`.
- `spring.jpa.properties.hibernate.default_schema=order_service`.

Indexes:

- `orders_tenant_submitted_at_idx`: the list.
- `orders_tenant_external_order_id_key`: idempotency.
- `orders_tenant_id_id_key`: the items FK target.
- `patients_pkey`: the existence check and the FK target.
- `order_items_tenant_order_idx`: item fetches and the cascade.
- The primary keys.

Not indexed: `status`, `order_type`, `priority`, `orders (tenant_id, patient_id)`.

Write path: everything runs in one transaction.

1. Trim and validate the request.
2. Hash it with SHA-256.
3. Check that the tenant and the patient exist; if either doesn't, return 422. The FKs are the backstop.
4. `INSERT orders ... ON CONFLICT DO NOTHING RETURNING id`, with the id from `DEFAULT uuidv7()`.
5. If a row came back, insert the items, commit, and return 201.
6. If no row came back, read the existing order's `request_hash`. Equal hash: 200 with the original id. Different hash: 409.

**`request_hash` coverage** (ADR-002 repeats this list):

- **Included:**
  - `patient.patient_id`
  - `order_type`
  - `priority`
  - `items[]` (`code`, `description`, `quantity`), sorted by `(code, description, quantity)` so item order doesn't matter. Repeated items still count.
  - `notes`
- **Excluded:**
  - `tenant_id` and `external_order_id`, because they are the key.
  - `status`, because the server owns it.
  - `submitted_at`, the captain's round 3 call.
  - `submitted_by.user_id` and `display_name`, the captain's round 5 call.

The hash is computed over the validated, trimmed values in a fixed field order. A replay keeps the stored originals of every excluded field.

API contract implied (README deviations from the canonical schema):

- `patient` is `{ "patient_id" }` only.
- `POST` reads `tenant_id` from the JSON body. `GET /orders` reads the `X-Tenant-Id` header, as a temporary measure; the production answer is a JWT tenant claim.
- Ids are trimmed before use.
- Unknown tenant or patient: 422. Replay: 200. Conflict: 409.

## Decisions

| ID | Decision | Recommendation | Rejected | Why |
|---|---|---|---|---|
| D1 | Keys (**captain**) | `orders.id` and `order_items.id`: `uuid DEFAULT uuidv7()`, generated by the DB. JPA `@GeneratedValue(IDENTITY)` on the order id. `patients (tenant_id, id)` | App-generated UUIDv7 (rounds 0-5); bigint; UUIDv4 | Round 6 question: nothing prevents DB generation. App-side generation was a preference: I wanted the id before the INSERT, and Hibernate 6.6 has no v7 generator. Verified: Hibernate reads the DB default back. One source of ids, and no extra dependency. Cost: no JDBC batching of order inserts, which doesn't matter at one order per request. |
| D2 | Enums (**captain**) | `order_type char(1)` L/I/M/C, `priority char(1)` R/U/S; `Character` converters | PG ENUM; lookup tables | Verified validation and the CHECKs. |
| D3 | Patients (**captain**) | Read-only reference data; the request carries `patient_id` only; existence check returns 422; `@Immutable` entity with `@IdClass` | Upsert; creating unknown patients | Patient data lives outside order intake. |
| D4 | Order items (**captain**) | uuid PK from the DB default; `line_no` plain; composite FK; `(tenant_id, order_id)` index; `@ElementCollection` | Composite PK; jsonb; `@Entity` | Verified: the insert omits `id`. |
| D5 | Status (**captain**) | `status text NOT NULL DEFAULT 'submitted'`, **no CHECK**; the API validates; no history table | CHECK `('submitted')` (rounds 0-5) | Round 6 call. Trade-off: a writer that bypasses the API can store any text (verified). |
| D6 | Idempotency (**captain**) | Unique key + `request_hash` (coverage above); 200/409; ADR-002 lists the coverage | Idempotency-Key + TTL; field compare; raw payload | Replays don't depend on stamps, item order, or the submitter. |
| D7 | Isolation (**captain**) | `tenant_id` everywhere + composite FKs + RLS (V3); only `orders_admin_user` bypasses; ADR-001 | Schema/DB per tenant; app-only | Verified that cross-tenant reads and FKs fail. |
| D8 | Timestamps & locking (**captain**) | timestamptz; **`version bigint NOT NULL DEFAULT 0` back**, `@Version Long` | No version + `Persistable` (rounds 1-5) | Round 6 call. With a DB-generated id, Spring Data detects new entities by the null id, so `Persistable` isn't needed. Verified: a single INSERT, with `version = 0`. |
| D9 | Trimming (**captain**) | The API strips; a DB CHECK refuses untrimmed keys; case-sensitive | Trigger; case-folding | Verified the rejects. |
| D10 | Reference data access (**captain**) | The app role has `SELECT` only on tenants and patients | Full DML | Verified "permission denied". |
| D11 | Tenant identification (**captain**) | POST: body `tenant_id`. GET: `X-Tenant-Id` header (temporary). Unauthenticated; unknown returns 422. Production: JWT claim | Query param; API keys | Security is out of scope. Primary keys stay out of URLs. |
| D15 | Database identities | Flyway as `orders_owner`, the app as `orders_app`, people as `orders_admin_user`; the superuser only bootstraps roles; tests wire the users explicitly with `DynamicPropertyRegistrar` | Flyway as the compose superuser; `@ServiceConnection` | Round 7 question. Least privilege and explicit ownership. `@ServiceConnection` ran the app as the superuser, bypassing RLS (verified). |
| D12 | Partitioning, naming | None; plural snake_case, explicit constraint names | Partitions; singular | No scale figures. `order` is reserved. |
| D13 | Schema (**captain**) | Everything in `order_service`; Flyway `schemas` + Hibernate `default_schema`; fully qualified DDL | `public`; relying on `search_path` | Round 6 call: modularity at the DB level. Verified: `validate` passes, and the history table is in `order_service`. |
| D14 | Admin role (**captain**) | `orders_admin_user` `BYPASSRLS`; `SELECT` on all tables; `INSERT`/`UPDATE`/`DELETE` on the four service tables by name; default privileges for future tables; no DDL, no writes to Flyway's history | Read-only (round 6); a superuser; `ON ALL TABLES` writes | Round 6 and 8 calls. Verified: cross-tenant writes work, FKs still hold, history and DDL are refused. It reads and changes PHI, so production should audit it (pgaudit). |

## Open questions

None.

Closed:

- Auth: none.
- RLS: ships, ADR-001.
- List filter: `submitted_at` range.
- Patient names: out of the request.
- Codes: `L/I/M/C`, `R/U/S`.
- Unknown patient: 422.
- GET tenant: `X-Tenant-Id` header.
- Hash coverage: as listed above.
- Ids: DB-generated.

Parked by the captain: what to do if a submitted patient name ever differs from the record. It's moot while the request carries no names.

Assumptions shown on the page:

- **A1** Lengths:
  - ids: 100
  - display name: 50
  - first and last name: 30
  - tenant name: 100
  - item code: 64
  - item description: 500
  - notes: 2000
- **A2** `patient` is `{patient_id}` only.
- **A3** Ids are trimmed and compared case-sensitively.
- **A4** Responses:
  - replay: 200
  - conflict: 409
  - unknown tenant or patient: 422
  - `GET /orders` reads `X-Tenant-Id`
- **A5** A list page returns `patient_id` only.
- **A6** Two ADRs: ADR-001 tenant isolation, ADR-002 idempotency with the hash coverage.
- **A7** Removed in round 8: the admin role now writes.

## Evidence

### Round 8 - PostgreSQL 18.6 (`01-roles.sql` as superuser; schema, a stand-in `flyway_schema_history`, V2, and V3 applied as `orders_owner`)

```
V2 + V3 applied as orders_owner (non-superuser): APPLIED_AS_OWNER
admin, no tenant set: INSERT tenants/patients/orders for A and B -> INSERT 0 2 (x3)
admin: UPDATE orders SET notes -> UPDATE 2 (both tenants); UPDATE patients (A) -> UPDATE 1; DELETE orders (B) -> DELETE 1
admin: order -> patient P-NOPE -> ERROR: violates foreign key constraint "orders_patient_fk"
admin: SELECT flyway_schema_history -> 1 row; DELETE -> ERROR: permission denied for table flyway_schema_history
admin: CREATE TABLE -> ERROR: permission denied for schema order_service
owner creates order_service.later -> admin INSERT 0 1 / DELETE 1 (default privileges)
```

### Round 7 - Spring Boot 3.5.16 + Testcontainers `postgres:18` (`./gradlew test --tests '*RolesLabTests*'`)

```
-- with @ServiceConnection + spring.datasource.username=orders_app:
LAB app datasource user: test                      <- property ignored; superuser (rolsuper=t, rolbypassrls=t)
-- with DynamicPropertyRegistrar (url, orders_app, flyway.user=orders_owner), withInitScript("01-roles.sql"):
LAB app datasource user: orders_app
LAB table owners: flyway_schema_history, order_items, orders, patients, tenants -> orders_owner
LAB schema owner: orders_owner
LAB app sees orders without tenant: 0
LAB app CREATE TABLE: ERROR: permission denied for schema order_service
(app reading flyway_schema_history: ERROR: permission denied for table flyway_schema_history)
pg_roles: orders_owner (super f, bypassrls f), orders_app (f, f), orders_admin_user (f, t), test (t, t)
BUILD SUCCESSFUL
```

### Round 6 - PostgreSQL 18.6 (fresh database; `01-roles.sql`, `CREATE SCHEMA order_service`, V2, V3)

```
\du: orders_admin_user | Bypass RLS ; orders_app | (none)
\dt order_service.*: order_items, orders, patients, tenants (owner orders)
app role, tenant A: INSERT ... RETURNING -> uuid_extract_version(id) = 7, version = 0, status = submitted
app role, tenant B: INSERT ... status = 'anything-goes-now' -> accepted (no CHECK); b_sees = 1
app role, no tenant set: app_no_tenant = 0
admin role, no tenant set: admin_sees_orders = 2 across 2 tenants; admin_sees_patients = 2   (BYPASSRLS despite FORCE)
admin role: INSERT tenants / UPDATE orders / DELETE order_items -> ERROR: permission denied for table ...
owner creates order_service.later_table -> admin reads it immediately (ALTER DEFAULT PRIVILEGES)
```

### Round 6 - Spring Boot 3.5.16 / Hibernate 6.6.53 (`./gradlew test --tests '*MappingLabTests*'`, ddl-auto=validate)

Settings: `spring.flyway.schemas=order_service`, `hibernate.default_schema=order_service`. Output:

```
BUILD SUCCESSFUL
LAB flyway history in: order_service
Hibernate: select count(*) from order_service.patients pe1_0 where pe1_0.id=? and pe1_0.tenant_id=?
--- assigned id + @Version
Hibernate: insert into order_service.orders (...,version,id) values (...)          (no SELECT before it)
--- DB-generated id (IDENTITY) + @Version
Hibernate: insert into order_service.orders (...,version) values (...)              (no id column)
Hibernate: insert into order_service.order_items (order_id,tenant_id,line_no,...) values (...)
LAB db id returned to Java: 01a0fda0-2e0f-76b4-94c7-25fd599482e1 version=0         (v7 from uuidv7())
```

### Earlier rounds (still valid)

- **Indexes** (round 4, 600k items): items for a page of orders and Hibernate's collection load both use `order_items_tenant_order_idx`.
- **Reference data** (round 3): the app role is refused writes to tenants and patients; an unknown patient or tenant fails its FK (mapped to 422), and the FK DETAIL hides the key value.
- **Constraints:**
  - Trimming CHECKs reject a leading space, a trailing tab, and a newline.
  - Length CHECKs fire.
  - `char(1)` rejects `'X'`, lowercase, and whole words.
  - Every CHECK `DETAIL` echoes the full row, PHI included, so use pgjdbc `logServerErrorDetail=false`.
  - NUL in text is refused.
- **RLS:**
  - An unset tenant sees 0 rows. After the transaction, `current_setting` returns `''`, hence `NULLIF`.
  - Tenant B writing A's tenant_id is rejected.
  - The superuser bypasses RLS.
- **Idempotency:** replay `INSERT 0 0`. With 204k orders, the list and its next page use `orders_tenant_submitted_at_idx`.

Scratch sources live outside the repo, in the session scratchpad: `pg/` (current `01-roles.sql`, V2, V3), `pg/r1/` through `pg/r6/` (earlier DDL and test SQL), and `jpa-lab/` through `jpa-lab-r6/`.

## Sharp edges for implementation

- **Roles and connections:** `01-roles.sql` must run first. Wire it into `../../compose.yaml` (`docker-entrypoint-initdb.d`) and `PostgresTestConfiguration` (`withInitScript`). The app connects as `orders_app` (`spring.datasource.username`); Flyway connects as `orders_owner` (`spring.flyway.user`).
- **Don't use `@ServiceConnection`** or the compose service connection for Postgres: they force the superuser and override both users. Use a `DynamicPropertyRegistrar` in tests and explicit properties in dev. Add a test asserting `select current_user` = `orders_app`, so this can't regress silently.
- **Passwords:** the ones in `01-roles.sql` are dev/test only.
- **Transactions:** `set_config(..., true)` only lasts for the transaction, so tenant work must run inside `@Transactional`.
- **PHI in errors:** turn pgjdbc `logServerErrorDetail` off, and never log raw constraint errors.
- **Hash:** strip ids, sort items, and hash exactly the ADR-002 field list in a fixed order.
- **Error mapping:**
  - `orders_patient_fk` and `orders_tenant_id_fkey` violations: 422.
  - `orders_tenant_external_order_id_key`: the duplicate path.
- **Status:** with no DB CHECK, the API must reject any value other than `submitted`.
- **Check order:** the existence check runs before the duplicate check. A replay whose patient has since been removed upstream gets 422; acceptable while patients aren't deleted.
- **Edge-case test candidates:**
  - a replay with a different `submitted_at` or `submitted_by`, or with reordered items, returns 200
  - `" PO-1 "` replaying `"PO-1"` returns 200
  - an unknown patient returns 422
  - NUL in `notes` is rejected

## Proposed DDL (round 8: `01-roles.sql` adds `orders_owner`; V3 grants the admin writes)

```sql
-- 01-roles.sql: cluster-wide roles, run once per cluster by the bootstrap superuser
-- (POSTGRES_USER) via docker-entrypoint-initdb.d / Testcontainers withInitScript.

-- Flyway: owns the order_service schema and every table in it. Not a superuser.
CREATE ROLE orders_owner LOGIN PASSWORD 'orders_owner' NOSUPERUSER NOBYPASSRLS;
DO $$ BEGIN EXECUTE format('GRANT CREATE ON DATABASE %I TO orders_owner', current_database()); END $$;

-- The service's runtime connection: no superuser, no BYPASSRLS, owns nothing, so RLS applies.
CREATE ROLE orders_app LOGIN PASSWORD 'orders_app' NOSUPERUSER NOBYPASSRLS;

-- People debugging or supporting the service: reads and writes every tenant's rows.
CREATE ROLE orders_admin_user LOGIN PASSWORD 'orders_admin' NOSUPERUSER BYPASSRLS;
```

```sql
-- V2__orders.sql
-- Order intake schema, all inside the order_service schema (Flyway creates it via
-- spring.flyway.schemas=order_service and keeps its history table there).
-- Requires PostgreSQL 18 (uuidv7()).
-- tenants and patients are reference data maintained outside this service; it only reads them.
-- Identifiers are trimmed by the API before validation; the CHECKs below refuse
-- leading or trailing whitespace so an untrimmed value can never be stored or compared.

CREATE TABLE order_service.tenants (
    id          uuid        PRIMARY KEY,
    name        text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 100),
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- One row per patient per tenant. id is the patient id the client looked up before
-- submitting; it is only unique within a tenant.
CREATE TABLE order_service.patients (
    tenant_id   uuid NOT NULL REFERENCES order_service.tenants (id),
    id          text NOT NULL CHECK (char_length(id) BETWEEN 1 AND 100 AND id !~ '^\s|\s$'),
    first_name  text NOT NULL CHECK (char_length(first_name) BETWEEN 1 AND 30),
    last_name   text NOT NULL CHECK (char_length(last_name) BETWEEN 1 AND 30),

    PRIMARY KEY (tenant_id, id)
);

COMMENT ON TABLE  order_service.patients            IS 'Reference data maintained outside the order intake service (read-only here).';
COMMENT ON COLUMN order_service.patients.id         IS 'PHI: patient identifier (MRN-like), unique per tenant. Never log.';
COMMENT ON COLUMN order_service.patients.first_name IS 'PHI. Never log.';
COMMENT ON COLUMN order_service.patients.last_name  IS 'PHI. Never log.';

CREATE TABLE order_service.orders (
    id                         uuid        PRIMARY KEY DEFAULT uuidv7(),
    tenant_id                  uuid        NOT NULL REFERENCES order_service.tenants (id),
    external_order_id          text        NOT NULL CHECK (char_length(external_order_id) BETWEEN 1 AND 100
                                                           AND external_order_id !~ '^\s|\s$'),
    request_hash               bytea       NOT NULL CHECK (octet_length(request_hash) = 32),
    patient_id                 text        NOT NULL,
    order_type                 char(1)     NOT NULL CHECK (order_type IN ('L', 'I', 'M', 'C')),
    priority                   char(1)     NOT NULL CHECK (priority IN ('R', 'U', 'S')),
    status                     text        NOT NULL DEFAULT 'submitted',
    submitted_by_user_id       text        NOT NULL CHECK (char_length(submitted_by_user_id) BETWEEN 1 AND 100
                                                           AND submitted_by_user_id !~ '^\s|\s$'),
    submitted_by_display_name  text                 CHECK (char_length(submitted_by_display_name) <= 50),
    notes                      text                 CHECK (char_length(notes) <= 2000),
    submitted_at               timestamptz NOT NULL,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    version                    bigint      NOT NULL DEFAULT 0,

    -- Idempotency key: one order per tenant-supplied id, forever.
    CONSTRAINT orders_tenant_external_order_id_key UNIQUE (tenant_id, external_order_id),
    -- Target for the tenant-scoped composite FK from order_items.
    CONSTRAINT orders_tenant_id_id_key UNIQUE (tenant_id, id),
    -- Composite FK: an order can only reference an existing patient of the same tenant.
    CONSTRAINT orders_patient_fk FOREIGN KEY (tenant_id, patient_id)
        REFERENCES order_service.patients (tenant_id, id),
    -- Mirrors the API rule "submitted_at at most 24h in the future", relative to server receipt.
    CONSTRAINT orders_submitted_at_not_future CHECK (submitted_at <= created_at + interval '24 hours')
);

-- GET /orders: tenant-scoped, date-range filter on submitted_at, keyset pagination newest first.
CREATE INDEX orders_tenant_submitted_at_idx ON order_service.orders (tenant_id, submitted_at DESC, id DESC);

COMMENT ON COLUMN order_service.orders.order_type   IS 'L = lab, I = imaging, M = medication, C = consult.';
COMMENT ON COLUMN order_service.orders.priority     IS 'R = routine, U = urgent, S = stat.';
COMMENT ON COLUMN order_service.orders.status       IS 'Validated by the API; no DB constraint until a lifecycle exists.';
COMMENT ON COLUMN order_service.orders.patient_id   IS 'PHI: patients.id within this tenant. Never log.';
COMMENT ON COLUMN order_service.orders.notes        IS 'May contain PHI (free text). Never log.';
COMMENT ON COLUMN order_service.orders.request_hash IS 'SHA-256 of patient_id, order_type, priority, notes and the items (order-independent); see ADR-002.';
COMMENT ON COLUMN order_service.orders.version      IS 'JPA optimistic-locking version (@Version).';

CREATE TABLE order_service.order_items (
    id           uuid    PRIMARY KEY DEFAULT uuidv7(),
    tenant_id    uuid    NOT NULL,
    order_id     uuid    NOT NULL,
    line_no      integer NOT NULL CHECK (line_no >= 1),
    code         text    NOT NULL CHECK (char_length(code) BETWEEN 1 AND 64),
    description  text             CHECK (char_length(description) <= 500),
    quantity     integer NOT NULL CHECK (quantity >= 1),

    -- Composite FK: an item can only ever belong to an order of the same tenant.
    CONSTRAINT order_items_order_fk FOREIGN KEY (tenant_id, order_id)
        REFERENCES order_service.orders (tenant_id, id) ON DELETE CASCADE
);

-- Items of an order (or a page of orders); also serves the FK cascade.
CREATE INDEX order_items_tenant_order_idx ON order_service.order_items (tenant_id, order_id);
```

```sql
-- V3__tenant_rls.sql
-- Row-level security: the database itself refuses cross-tenant reads and writes.
-- The app sets the tenant once per transaction:
--   SELECT set_config('app.tenant_id', :tenantId, true);   -- true = transaction-local
-- Unset (or reset to '') means no rows visible and no rows writable: it fails closed.
-- Only effective when the app connects as a role that is not a superuser, has no
-- BYPASSRLS, and does not own the tables (FORCE also covers the owner).
-- The roles themselves are cluster-wide and created outside Flyway (see 01-roles.sql).

ALTER TABLE order_service.patients    ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_service.patients    FORCE  ROW LEVEL SECURITY;
ALTER TABLE order_service.orders      ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_service.orders      FORCE  ROW LEVEL SECURITY;
ALTER TABLE order_service.order_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE order_service.order_items FORCE  ROW LEVEL SECURITY;

CREATE POLICY patients_tenant_isolation ON order_service.patients
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY orders_tenant_isolation ON order_service.orders
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

CREATE POLICY order_items_tenant_isolation ON order_service.order_items
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- tenants stays outside RLS: it holds no tenant business data.

GRANT USAGE ON SCHEMA order_service TO ${appRole}, ${adminRole};

-- The service writes orders only; tenants and patients are read-only reference data.
GRANT SELECT, INSERT, UPDATE ON order_service.orders, order_service.order_items TO ${appRole};
GRANT SELECT ON order_service.tenants, order_service.patients TO ${appRole};

-- The admin role has BYPASSRLS (set when the role is created): it reads and writes every
-- tenant's rows for debugging, support, and data fixes. Writes are granted on the service's
-- tables by name so flyway_schema_history stays read-only to it.
GRANT SELECT ON ALL TABLES IN SCHEMA order_service TO ${adminRole};
GRANT INSERT, UPDATE, DELETE
    ON order_service.tenants, order_service.patients, order_service.orders, order_service.order_items
    TO ${adminRole};
ALTER DEFAULT PRIVILEGES IN SCHEMA order_service
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${adminRole};
```

## What should ship next

Once the captain approves, the follow-up implementation task is:

- Add V2 and V3 as Flyway migrations, plus `01-roles.sql` as a DB init script in `../../compose.yaml` and `PostgresTestConfiguration`.
- Configure:
  - `spring.flyway.schemas=order_service`
  - the placeholders
  - `spring.flyway.user=orders_owner`
  - a `DynamicPropertyRegistrar` instead of `@ServiceConnection`
  - `hibernate.default_schema=order_service`
  - an app datasource as `orders_app`
- Seed dev and test tenants and patients.
- Map the entities as prototyped:
  - an `@Version` order with an IDENTITY UUID id and `Character` converters
  - an `@ElementCollection` of items
  - an `@Immutable` patient
- Implement `POST /orders` (body `tenant_id`) and `GET /orders` (`X-Tenant-Id` header), with the 200/201/409/422 semantics.
- Write ADR-001 (tenant isolation via RLS, including the admin bypass) and ADR-002 (idempotency, including the hash field list).

## Review log

- Round 0 (2026-10-01): page opened in Lavish; `needs-decision` appended.
- Round 1 (2026-10-01): dropped `tenant_api_keys` and `version`; `order_type char(1)`; added the `patients` table.
- Round 2 (2026-10-01):
  - Patient id is internal, with PK `(tenant_id, id)`.
  - `priority char(1)`.
  - Ids trimmed.
  - New lengths.
  - RLS ships.
- Round 3 (2026-10-02):
  - Tenants and patients are read-only reference data; unknown returns 422.
  - No patient names in the request.
  - Body `tenant_id`.
  - `submitted_at` out of the hash.
  - `order_items` uuid PK.
- Round 4 (2026-10-02):
  - GET reads the tenant from the `X-Tenant-Id` header.
  - Item order out of the hash.
  - `(tenant_id, order_id)` index restored.
- Round 5 (2026-10-02): `submitted_by` out of the hash.
- Round 6 (2026-10-02):
  - Everything in the `order_service` schema.
  - `orders_admin_user` (BYPASSRLS, read-only).
  - `version` restored.
  - `status` CHECK dropped.
  - `orders.id` DB-generated: answered the captain's question and verified Hibernate IDENTITY on a UUID.

  DDL and JPA re-verified.
- Round 7 (2026-10-02): clarified who connects as what.
  - Added the non-superuser `orders_owner` role for Flyway.
  - Found and fixed the `@ServiceConnection` superuser trap.
  - Verified with Testcontainers.
- Round 8 (2026-10-02): `orders_admin_user` gets `INSERT`/`UPDATE`/`DELETE` on the four service tables, plus default privileges, but not on `flyway_schema_history`. Verified in psql with the schema built as `orders_owner`.
- Verdict (2026-10-02): the captain approved. Review concluded; ready for an implementation task (see "What should ship next").
