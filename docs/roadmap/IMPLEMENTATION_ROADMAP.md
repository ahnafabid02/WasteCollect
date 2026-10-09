# WasteCollect Implementation Roadmap

## Dependency order

M0 decisions and contracts → M1 foundation → M2 identity → M3 pickup requests → M4 grouping → M5 scheduling/admin → M6 collector operations → M7 notifications/reporting → M8 advanced logistics/AI → M9 integrations → M10 release hardening.

## M0 exit gate

- Feature inventory and decision log approved.
- Business rules and state matrices approved.
- Security/authorization design approved.
- ERD, constraints, indexes, and transaction boundaries reviewed.
- API contract and error format versioned.
- Frontend route and design-system plan approved.
- Test plan and acceptance scenarios approved.
- M1 issues contain dependencies and acceptance criteria.

## Issue template

Every implementation issue must include:

1. Objective and user outcome.
2. Scope and out-of-scope behavior.
3. Database/API/frontend surfaces.
4. Authorization and sensitive-data considerations.
5. Dependencies.
6. Acceptance criteria.
7. Test evidence required.
8. Documentation to update.

## M1 starter backlog

- Scaffold frontend and backend.
- Configure native PostgreSQL and Flyway with a repeatable PowerShell setup.
- Configure environment handling and quality tooling.
- Add health endpoint and application shells.
- Add CI build, lint, type-check, and test jobs.
- Verify fresh-checkout setup and document local development.
