# ADR-002: Idempotent order submission and request-hash coverage

Status: accepted

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
| `order_type`, `priority` | `status`: owned by the server |
| `notes` | `submitted_at`: a retry is the same order however the client stamps it |
| `items[]`: `code`, `description`, `quantity`, sorted by `(code, description, quantity)` | `submitted_by.user_id`, `submitted_by.display_name`: who resubmits does not change what was ordered |
| | item order: a reordered list is the same order; repeated items still count |

**Canonical encoding** (`RequestHash`): a JSON array in a fixed field order, built from
validated, trimmed values:

```
["v1", patient_id, order_type, priority, notes, [[code, description, quantity], ...]]
```

The items are sorted, and a missing description sorts first. JSON whitespace, key order, and
absent versus `null` optional fields cannot change the hash. The `v1` tag leaves room to change
the coverage later without collisions.

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
  still returns 200. Finally, a changed priority gives a 409, and the same `external_order_id`
  in another tenant is independent.
