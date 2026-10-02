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
