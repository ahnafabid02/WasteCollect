# WasteCollect Test Plan

## Implemented evidence through M5

- Spring integration tests cover registration, login, refresh-token rotation, logout revocation, resident ownership, request history, cancellation, administrator-only grouping, group confirmation, and collector provisioning.
- A real PostgreSQL concurrency test runs two overlapping group confirmations and verifies exactly one active membership is committed.
- Migration tests run Flyway through V5 against native PostgreSQL; no container runtime is required.
- Frontend lint, TypeScript checks, component tests, and production build are part of the verification gate.
- The signed-in workspace regression test verifies profile/catalog data renders, the loading indicator clears, and rerenders do not repeat the four initial API requests.
- Resident cancellation is limited to `PENDING` requests. M5 group cancellation releases active memberships/assignments and reconciles request status atomically.
- `OperationsIntegrationTests` verifies schedule/reschedule/reassignment/cancellation, historical preservation, rollback on conflicts, adjacent windows, notice/cutoff validation, collector availability, settings/group-size enforcement, search/pagination/sort validation, metrics, and resident/collector denial paths.
- `AssignmentConcurrencyTests` runs two actual concurrent PostgreSQL transactions and verifies exactly one overlapping assignment and its audit event commit. Fictional fixture rows are removed after the test.
- Administrator component tests cover six M5 routes, populated and empty states, request details/history, schedule submission, assignment conflicts, pagination, settings saves, and error recovery. Automated tests are not a substitute for final production browser/accessibility testing.

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
