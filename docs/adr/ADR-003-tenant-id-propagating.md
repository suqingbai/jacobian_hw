# ADR-003: How to pass tenant_id of inbound requests through the stack 

Status: draft

## Context

Every inbound request belongs to one tenant, we need secure, reliable, dev-friendly and monitoring-friendly way to pass tenant id from API layer to database layer.

## Options Considered

Tenant-id is deliberately simple for now in API request, because security is out of scope:

- `POST /v1/orders` takes `tenant_id` from the JSON body.
- `GET /v1/orders` takes it from the unauthenticated `X-Tenant-Id` header, avoiding from the wide visible URL.
- tenant_id is passed to service layer and bound to the transaction manually, so that RLS can work properly.

## Proposed Decision

1. Tenant_id should be extracted from a verified JWT claim at API security layer, and injected into the controller.
2. When a JSON request body contains tenant_id, the controller should verify that the tenant_id from JWT claim and the one from request body are the same, otherwise reject the request.
3. At service layer, instead of manually bind tenant_id to the transaction, suggest to use Spring AOP to bind tenant_id to the transaction automatically, so that developers don't have to remember to bind tenant_id for every transaction.
4. Suggest to consider require tenant_id in 'X-Tenant-Id` http header for all inbound requests, so that it is easier to monitor the traffic from web tier.
5. Avoid pass tenant_id as URL query parameter, because it is widely visible.

## Expected Consequences:
Tenant_id is passed through the stack in a secure, reliable, automatic and monitoring-friendly manner, and enforces stronger API security.

