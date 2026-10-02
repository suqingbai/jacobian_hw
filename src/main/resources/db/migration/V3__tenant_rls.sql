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
