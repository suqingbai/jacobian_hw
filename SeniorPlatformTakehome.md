# Senior Platform Engineer Take-Home Assessment

**Role:** Senior Backend / Platform Engineer
**Time Estimate:** 4–6 hours
**Stack:** Your choice of **TypeScript** (Node 20 LTS+) or **Java** (Spring Boot 3.x)
**Turnaround:** 72 hours from the time the brief is sent. If you need more, say so — we'd rather have a few extra days of good work than a rushed 6-hour sprint.

> **Important:** This exercise is intentionally **AI-forward**. We expect you to use AI early and throughout the assignment, not just at the end for cleanup. Your judgment in prompting, reviewing, correcting, and integrating AI output is part of what we are evaluating.
>
> You will submit a `PROMPTS.md` log of meaningful AI interactions. That file is a **graded deliverable**, so do not treat AI usage as optional or something to document only after the implementation is done.

> **Note on stack choice:** Our production codebase is TypeScript. If you choose Java, your post-hire ramp will include TS onboarding. Pick the stack where you'll write the strongest code in the available time.

> **Note on scope:** If you find yourself going significantly past 6 hours, stop and document what you cut. The brief is intentionally tight; we want to see your scope-control judgment, not 25 tests across three projects. "What I would do with more time" is its own section in your README — use it.

---

## Background

Your team operates a multi-tenant SaaS platform serving healthcare organizations. Each tenant is an organization with multiple users; one common workflow is **intake of structured order records** — purchase orders, lab orders, service requests — submitted by tenant-side systems via HTTP.

You're being asked to build the **order intake service**: the entry point that receives those submissions, validates them, persists them with strict tenant scoping, and exposes a query API for downstream consumers within the same tenant.

The service is small but production-shaped: real cloud-native patterns, real multi-tenant data isolation, real idempotency. Treat it as a starting point you could hand to the team for code review.

---

## Your Task

Design and build a small multi-tenant order intake service. You decide the runtime, the framework, the database, and the architectural shape — your choices and trade-off reasoning are part of what we evaluate.

As you work, use AI as part of your normal development process and capture that process in `PROMPTS.md` as you go. We are assessing how you collaborate with AI, not whether you avoided it.

### Requirements

1. **REST API** with two endpoints (at minimum):

   - `POST /orders` — accept an order submission. Validate against the schema in § "Order Schema" below. Persist on success; return `201 Created` with the order ID. Return structured validation errors on failure.
   - `GET /orders` — list orders for the calling tenant. Support **at least one** query parameter (status, date range, or your choice — justify the choice).

2. **Multi-tenant isolation.**

   - Each request is scoped to a single tenant. You decide the mechanism and justify it in your ADR (§ 5).
   - Tenant A's orders **must not** be visible to Tenant B under any code path. A test must demonstrate this.

3. **Idempotency.**

   - Repeated submissions of the same logical order must not result in duplicate persistence. You decide the mechanism, the conflict-handling semantics, and any storage / retention behavior. Document your choices in the ADR or README.

4. **Persistence.**

   - Choose a persistent store appropriate to the problem; justify the choice in your README.
   - Provide a `docker-compose.yml` that brings up the service + its database.
   - Schema design is part of the deliverable — include the SQL or migration files.

5. **One Architecture Decision Record (ADR).**

   - One page, plain markdown.
   - Pick one design choice you made and explain the alternatives you considered + why you chose what you did.
   - Examples worth picking: synchronous vs async ingestion; idempotency mechanism; tenant-isolation mechanism; how you handle partial-write / database-transaction boundaries; observability surface.

6. **Test coverage.** At minimum:

   - A happy-path order submission.
   - A validation-failure case with structured error response.
   - A multi-tenant isolation case (Tenant B cannot read Tenant A's orders).
   - An idempotency case (a repeated submission of the same logical order does not result in duplicate persistence).
   - One edge case of your own choosing — document why you chose it.

   Integration tests against the real database via the docker-compose stack are preferred over heavily-mocked unit tests, but either is acceptable if you justify the choice.

---

## Order Schema

Use the following as the canonical order shape. Define types in your runtime (TS interfaces or Java records / DTOs).

```json
{
  "tenant_id": "string (UUID)",
  "external_order_id": "string (tenant-supplied; unique per tenant)",
  "submitted_by": {
    "user_id": "string",
    "display_name": "string"
  },
  "patient": {
    "patient_id": "string",
    "name": "string"
  },
  "order_type": "lab | imaging | medication | consult",
  "priority": "routine | urgent | stat",
  "status": "submitted",
  "items": [
    {
      "code": "string",
      "description": "string",
      "quantity": "integer (>= 1)"
    }
  ],
  "notes": "string (optional, max 2000 chars)",
  "submitted_at": "ISO-8601 datetime"
}
```

**Validation rules** (enforce in your service):

- `tenant_id`, `external_order_id`, `submitted_by.user_id`, `patient.patient_id`, `order_type`, `priority`, `submitted_at` are required.
- `order_type` must be one of the enumerated values; same for `priority`.
- `items` must have at least one element; each element's `quantity` must be an integer ≥ 1.
- `notes` is optional; reject if longer than 2000 characters.
- `external_order_id` must be unique per tenant — a second submission of the same `(tenant_id, external_order_id)` triggers idempotency handling (§ Requirement 3).
- `submitted_at` must parse as ISO-8601 and must not be more than 24 hours in the future.

Where the schema is ambiguous, make a reasonable choice and document it in the README.

---

## Sample Request

```http
POST /orders HTTP/1.1
Content-Type: application/json
Authorization: <tenant-scoping mechanism your choice>

{
  "tenant_id": "11111111-1111-1111-1111-111111111111",
  "external_order_id": "PO-2026-001",
  "submitted_by": { "user_id": "u-001", "display_name": "Jane Doe" },
  "patient": { "patient_id": "P-345678", "name": "Robert Jones" },
  "order_type": "lab",
  "priority": "routine",
  "status": "submitted",
  "items": [
    { "code": "CBC", "description": "Complete Blood Count", "quantity": 1 }
  ],
  "notes": "Pre-op screening",
  "submitted_at": "2026-05-19T14:30:00.000Z"
}
```

---

## PROMPTS.md — Required Artifact

**This is a graded deliverable.**

Include a `PROMPTS.md` file at the root of your submission that documents your AI-assisted development process. Start this file early rather than reconstructing it at the end. For each meaningful AI interaction, include:

- **What you asked** — the prompt or a paraphrase if it was conversational
- **What you got** — a brief description of the output
- **What you changed or rejected** — what you accepted as-is, what you modified, and what you discarded
- **Why** — one to two sentences on your reasoning

There is no right number of entries. A candidate who used AI once and wrote everything else manually should say so. A candidate who used AI heavily should be honest about what was accepted vs. corrected. **We are looking for engineering judgment, not AI avoidance or AI dependence.**

### Example entry format

```markdown
## Prompt 5 — Structured error response shape for validation failures

**Asked:** "Suggest a JSON shape for returning multiple validation errors from a single request — e.g., when POST /orders has both an invalid order_type and a missing required field. Should the response use a flat list, a per-field map, or RFC 7807?"

**Got:** Three options with pros/cons; the AI recommended a per-field map for client convenience.

**Changed:** I went with a hybrid — a top-level RFC 7807 Problem object plus an `errors` array where each entry has `field`, `code`, and `message`. The AI's per-field map would have made multiple errors per field hard to express (e.g., "required AND too long" as a single field).

**Why:** RFC 7807 gives downstream clients a recognizable envelope; the per-field-error array inside it accommodates multiple errors per field without forcing the nested-array-inside-a-map shape the AI proposed. Slightly more verbose than the flat map, but tooling-friendly.
```

---

## Deliverables

Submit a zip file or GitHub repository containing:

| File / Folder | Required |
|---|---|
| Source code (service + routes + persistence layer) | ✅ |
| Typed domain model (TS interfaces or Java records / DTOs) | ✅ |
| Database schema / migrations | ✅ |
| `docker-compose.yml` (service + database) | ✅ |
| Integration / unit tests | ✅ |
| One Architecture Decision Record (`ADR-001-<title>.md`) | ✅ |
| `PROMPTS.md` | ✅ |
| `README.md` (your own, replacing this file) | ✅ |

Your `README.md` should include:

- How to run the project, the database, and the tests
- Your stack choice and a one-paragraph rationale
- Any trade-offs or known limitations
- What you would do differently with more time

---

## Evaluation

Your submission will be evaluated across five dimensions:

| Dimension | Weight |
|---|---|
| Prompt Quality & AI Judgment | 25% |
| Technical Execution | 25% |
| Architectural Judgment (ADR + design choices) | 20% |
| Test Coverage & Quality | 15% |
| Communication & Documentation | 15% |

Detailed scoring criteria are available from your recruiting contact.
