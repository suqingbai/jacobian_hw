# ADR-002: Idempotent order submission and request-hash coverage

Status: accepted; amended 2026-10-02 (status added to the request hash)

## Context

Tenant systems resubmit orders on retries and timeouts. The assignment requires that a repeated
submission of the same logical order is not stored twice, and that `external_order_id` is unique
per tenant. A retry must get back the original order. A different order reusing an
`external_order_id` must be rejected, not silently treated as a retry.

## Decision

**The key is the business key.** `UNIQUE (tenant_id, external_order_id)` on `orders` decides
whether an order already exists. The key lives as long as the order, so there is no expiry to
configure. `external_order_id` is trimmed before use and compared case-sensitively.

**The write path** runs in one transaction, bound to the tenant (ADR-001):

1. Validate and trim the request, and compute `request_hash`.
2. Check that the tenant and the patient exist; if not, return 422. The service never creates
   them.
3. `INSERT ... ON CONFLICT (tenant_id, external_order_id) DO NOTHING RETURNING id`.
   - **A row came back:** insert the items and return **201** with the new order.
   - **No row:** read the stored `request_hash`. If it is equal, return **200** with the
     original order, as stored: its id, `submitted_at`, and submitter. If it differs, return
     **409** with `existing_order_id`.

A concurrent identical request waits on the unique index, then takes the "no row" branch. So two
racing retries can't both insert.

**`request_hash` coverage.** SHA-256, stored as `bytea`:

| Included | Excluded, and why |
|---|---|
| `patient.patient_id` | `tenant_id`, `external_order_id`: they are the key |
| `order_type`, `priority` | `submitted_at`: a retry is the same order however the client stamps it |
| `status`, after an omitted one defaults to `submitted` (amended 2026-10-02) | `submitted_by.user_id`, `submitted_by.display_name`: who resubmits does not change what was ordered |
| `notes` | item order: a reordered list is the same order; repeated items still count |
| `items[]`: `code`, `description`, `quantity`, sorted by `(code, description, quantity)` | |

**Canonical encoding** (`RequestHash`): a JSON array in a fixed field order, built from
validated, trimmed values:

```
["v2", patient_id, order_type, priority, status, notes, [[code, description, quantity], ...]]
```

The items are sorted, and a missing description sorts first. JSON whitespace, key order, and
absent versus `null` optional fields cannot change the hash, and neither can an omitted versus
an explicit `"status": "submitted"`. The version tag keeps hashes of different coverage from
colliding; `v1` lacked `status`.

## Amendment 2026-10-02: `status` is part of the hash

At the captain's request, the order's effective status now counts toward "the same logical
order". The original reason for leaving it out (the server owns it) no longer holds: the client
sends it on submission, and a submission is a statement of which state the order enters in. An
omitted status defaults to `submitted` before hashing, so it hashes like an explicit
`"submitted"`, and the hash uses the API wire value. The stored `status` column is now written
from the same validated value. The hash records the status as submitted, not the current
one, so a later lifecycle change to the stored row still cannot turn a replay into a 409.

- The canonical version went from `v1` to `v2`.
- Only `submitted` is accepted today, so no request can yet produce a status-only 409. The
  coverage takes effect once more submission statuses exist.
- Rows written before this change hold `v1` hashes, so replaying such an order gives a false
  409. There is no production data, so there is no backfill and no dual-version comparison:
  reset dev data with `docker compose down -v`; test databases are created fresh per run. A
  later coverage change with real data would need one of those.

## Alternatives considered

- **An `Idempotency-Key` header plus an `idempotency_keys` table with a TTL.**
  - It duplicates the business key as a second source of truth.
  - Once a key expires, a second order with the same `external_order_id` could be created,
    which contradicts "unique per tenant".
- **Comparing the stored row field by field.** Any later change to the stored order (a status
  update, say) would turn a genuine replay into a false 409. Every new column would also have
  to be added to the comparison.
- **Storing the raw request payload.** It copies PHI into another column and still needs a
  canonical comparison.
- **Catching the unique-violation exception instead of `ON CONFLICT`.** It works, but it uses
  exceptions for control flow and needs a second transaction after the rollback.

## Consequences

- **A replay returns the stored original.** Fields left out of the hash, such as a newer
  `submitted_at` or a different submitter, are not applied.
- **The coverage list above is part of the API contract.** Changing it means bumping the
  canonical version, and stored hashes keep their meaning.
- **The tenant and patient checks run before the duplicate check.** So if a patient were ever
  removed upstream, a later replay would get 422 instead of 200. That's acceptable while
  patients are never deleted.
- **Tests cover** a 201, then a 200 for an identical replay. They also cover a replay that
  changes `submitted_at`, the submitter, or item order, or adds whitespace around the id, which
  still returns 200, and an omitted versus an explicit `submitted` status. Finally, a changed priority gives a 409, and the same `external_order_id`
  in another tenant is independent.
