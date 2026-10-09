# WasteCollect Test Plan

## Implemented evidence through M4

- Spring integration tests cover registration, login, refresh-token rotation, logout revocation, resident ownership, request history, cancellation, administrator-only grouping, group confirmation, and collector provisioning.
- A real PostgreSQL concurrency test runs two overlapping group confirmations and verifies exactly one active membership is committed.
- Migration tests run Flyway through V4 against native PostgreSQL; no container runtime is required.
- Frontend lint, TypeScript checks, component tests, and production build are part of the verification gate.
- Through M4, resident cancellation is limited to `PENDING` requests. Later operational cancellation and membership/count reconciliation belongs to the scheduling workflow introduced after M4.

## Test layers

| Layer | Coverage |
|---|---|
| Unit | Validation, grouping eligibility, status transitions, cancellation, retry decisions |
| Integration | PostgreSQL mappings, constraints, transactions, migrations, API responses |
| Authorization | Role checks, ownership, assignment scope, administrator overrides |
| Concurrency | Two administrators confirming overlapping requests; duplicate assignment prevention |
| Frontend | Forms, validation, permissions-driven navigation, loading/error/empty states |
| E2E | Register → request → group → schedule → assign → collect → resident tracking |
| Performance | Grouping candidates, paginated lists, dashboard queries |
| Release | Migration, backup/restore, HTTPS, secrets, health checks, rollback |

## Required acceptance scenarios

- A resident cannot read another resident's request.
- A collector cannot update an unassigned or differently assigned request.
- A past pickup date is rejected.
- Two concurrent confirmations cannot create duplicate active memberships.
- Cancellation updates membership and operational counts correctly.
- Failed attempts remain in history after retry or reassignment.
- Dashboard totals reconcile to authoritative records.
- Browser refresh preserves persisted data.

## Test data

Use fictional residents, administrators, collectors, zones, categories, requests, groups, assignments, failures, retries, and notifications. Never use real addresses, credentials, or personal data in automated tests.

## Exit evidence

Every milestone links test results, known failures, and risk acceptance to its issue or pull request. A red security, data-integrity, migration, or core E2E test blocks the milestone exit gate.
