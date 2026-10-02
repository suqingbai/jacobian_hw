-- Reference data for the dev profile and the tests only (added to spring.flyway.locations there).
-- In production, tenants and patients are maintained by other systems; this service never writes them.
-- Flyway runs as orders_owner, which FORCE row-level security also binds, so each tenant's
-- patients are inserted with that tenant set for the transaction.

INSERT INTO order_service.tenants (id, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme Clinic'),
    ('22222222-2222-2222-2222-222222222222', 'Beacon Health')
ON CONFLICT (id) DO NOTHING;

SELECT set_config('app.tenant_id', '11111111-1111-1111-1111-111111111111', true);
INSERT INTO order_service.patients (tenant_id, id, first_name, last_name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'P-345678', 'Robert', 'Jones'),
    ('11111111-1111-1111-1111-111111111111', 'P-100001', 'Ana', 'Silva')
ON CONFLICT (tenant_id, id) DO NOTHING;

SELECT set_config('app.tenant_id', '22222222-2222-2222-2222-222222222222', true);
INSERT INTO order_service.patients (tenant_id, id, first_name, last_name) VALUES
    ('22222222-2222-2222-2222-222222222222', 'P-345678', 'Maria', 'Garcia'),
    ('22222222-2222-2222-2222-222222222222', 'P-200001', 'Ken', 'Ito')
ON CONFLICT (tenant_id, id) DO NOTHING;
