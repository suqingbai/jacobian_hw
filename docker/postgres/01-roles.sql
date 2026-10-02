-- 01-roles.sql: cluster-wide roles, run once per cluster by the bootstrap superuser
-- (POSTGRES_USER) from /docker-entrypoint-initdb.d. Both compose.yaml and
-- PostgresTestConfiguration mount this file there. The passwords are for dev and tests only.

-- Flyway: owns the order_service schema and every table in it. Not a superuser.
CREATE ROLE orders_owner LOGIN PASSWORD 'orders_owner' NOSUPERUSER NOBYPASSRLS;
DO $$ BEGIN EXECUTE format('GRANT CREATE ON DATABASE %I TO orders_owner', current_database()); END $$;

-- The service's runtime connection: no superuser, no BYPASSRLS, owns nothing, so RLS applies.
CREATE ROLE orders_app LOGIN PASSWORD 'orders_app' NOSUPERUSER NOBYPASSRLS;

-- People debugging or supporting the service: reads and writes every tenant's rows.
CREATE ROLE orders_admin_user LOGIN PASSWORD 'orders_admin' NOSUPERUSER BYPASSRLS;
