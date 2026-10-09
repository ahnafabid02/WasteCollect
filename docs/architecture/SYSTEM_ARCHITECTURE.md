# WasteCollect System Architecture

## Approved architecture

WasteCollect uses a modular monolith:

- React + TypeScript + Vite frontend
- Spring Boot REST backend
- PostgreSQL system of record
- Flyway schema migrations
- Native PostgreSQL local dependency with scripted role/database setup
- GitHub Actions CI

The backend modules are `auth/users`, `servicearea`, `wastecategory`, `pickup`, `grouping`, `scheduling`, `collector`, `notification`, `reporting`, `integration`, and `common`.

## Boundary rules

- Controllers translate HTTP and delegate to application services.
- Business rules live in services/domain components.
- Repositories do not decide authorization or lifecycle transitions.
- The backend validates role and record ownership on every protected operation.
- Database constraints protect persisted invariants in addition to application checks.
- Integration adapters isolate external providers from core domain modules.

## Core flow

Resident request → eligibility query → grouping suggestion → administrator confirmation → scheduling → collector assignment → pickup attempt → terminal outcome → notifications/reporting.

## Reliability

Transactions cover group confirmation, membership changes, status transitions, and assignments. Important state-changing APIs accept an idempotency key where duplicate delivery could create operational harm. Audit and history records are written with the business change.

## Deployment boundary

The frontend, backend, and managed PostgreSQL may have independent deployment lifecycles. Production configuration supplies secrets externally, requires HTTPS, restricts CORS, and exposes health and structured logs.
