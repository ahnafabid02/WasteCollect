# WasteCollect M0 Test Plan

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
