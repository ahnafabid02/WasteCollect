# WasteCollect Implementation Task List

Source: [WasteCollect_Development_Plan.md](./WasteCollect_Development_Plan.md)

Detailed task descriptions are organized by phase in the [task-description index](./docs/task-descriptions/README.md).

## 14. Implementation Task List

Use this checklist as the execution backlog. Complete tasks in dependency order, link each task to an issue or pull request, and do not mark a milestone complete until its exit gate has been verified.

### 14.1 M0 — Requirements Finalization and System Design

- [x] Review the SRS and finalize the complete feature inventory.
- [x] Record unresolved product decisions, owners, and target dates.
- [x] Decide supported cities/zones, zone lifecycle rules, quantity units, and date rules.
- [x] Finalize pickup and collection-group status-transition matrices.
- [x] Define cancellation cutoffs, rescheduling, failure, retry, reassignment, and partial-completion policies.
- [x] Decide initial group capacity limits and the concurrency-control strategy.
- [x] Define role-level and record-ownership authorization rules.
- [x] Draw and review the ERD, foreign keys, constraints, indexes, and retention requirements.
- [x] Define the versioned REST API contract, DTOs, pagination, validation errors, and idempotency rules.
- [x] Approve frontend routes, shared design-system responsibilities, and feature boundaries.
- [x] Define the test plan, critical acceptance scenarios, seed data, and release gates.
- [x] Create the architecture, domain, database, business-rules, security, API, roadmap, and test-plan documents.
- [x] Break M1–M10 into implementable issues with dependencies and acceptance criteria.
- [x] Approve the M0 exit gate before starting implementation.

### 14.2 M1 — Project Foundation

- [x] Create the monorepo directory structure and baseline README.
- [x] Scaffold the React/Vite/TypeScript frontend.
- [x] Scaffold the modular Spring Boot backend.
- [x] Configure native PostgreSQL and Flyway for local development.
- [x] Configure Flyway and create the initial migration convention.
- [x] Add environment configuration and safe local development defaults.
- [x] Configure formatting, linting, type checking, and baseline test runners.
- [x] Add the backend health-check endpoint and frontend application shell.
- [x] Add GitHub Actions for build, lint, type check, and test validation.
- [x] Verify a fresh checkout starts frontend, backend, and database successfully.
- [x] Verify the M1 exit gate and document local setup instructions.

### 14.3 M2 — Authentication and User Management

- [x] Implement user, role, account-status, and profile persistence.
- [x] Implement registration with validation and secure password hashing.
- [x] Implement login, logout, short-lived access tokens, and refresh-session rotation/revocation.
- [x] Implement backend authentication filters and role-based authorization.
- [x] Implement admin provisioning and secure collector creation/invitation.
- [x] Implement current-user profile retrieval and updates.
- [x] Implement frontend session handling and protected role-based routes.
- [x] Add ownership, privilege-escalation, token, and session-security tests.
- [x] Verify unauthorized and cross-role access is rejected.
- [x] Verify the M2 exit gate.

### 14.4 M3 — Waste Pickup Request Management

- [x] Create service-zone and waste-category migrations, seed data, and APIs.
- [x] Implement pickup-request entity, public request code, validation, and persistence.
- [x] Implement address, zone, quantity/unit, preferred-date, notes, and status handling.
- [x] Implement resident create, list, detail, history, and eligible-cancellation APIs.
- [x] Build the resident dashboard, request form, confirmation, detail, and history views.
- [x] Add validation, error, loading, empty, and refresh-state handling.
- [x] Add repository, service, API, authorization, and real-database integration tests.
- [x] Verify persisted requests survive browser refresh and cannot be read by other residents.
- [x] Verify the M3 exit gate.

### 14.5 M4 — Automatic Grouping Engine

- [x] Implement grouping eligibility queries and zone/date partitioning.
- [x] Implement draft grouping suggestions without changing committed memberships.
- [x] Implement administrator review and manual group adjustment.
- [x] Implement atomic group confirmation with revalidation.
- [x] Add active-membership uniqueness constraints and membership history.
- [x] Record status changes and audit events during confirmation.
- [x] Add transaction, locking, duplicate-prevention, and concurrent-confirmation tests.
- [x] Verify the grouping service can later support capacity or GPS strategies.
- [x] Verify the M4 exit gate.

### 14.6 M5 — Scheduling and Administrator Operations

- [x] Implement administrator request and group search, filtering, sorting, and pagination.
- [x] Implement group scheduling, rescheduling, lifecycle transitions, and validation.
- [x] Implement collector management, availability checks, assignment, and reassignment.
- [x] Implement administrator dashboard metrics from authoritative records.
- [x] Implement audit history for operational changes.
- [x] Build administrator request, grouping, scheduling, collector, and settings views.
- [x] Add API, authorization, concurrency, and workflow integration tests.
- [x] Verify admins can organize, schedule, and assign real stored collections.
- [x] Verify the M5 exit gate.

### 14.7 M6 — Collector Operations and Status Tracking

- [ ] Implement collector assigned-group and daily-worklist APIs.
- [ ] Implement assignment-scoped access checks for every collector operation.
- [ ] Implement start-work, pickup-attempt, completed, failed, and retry-related workflows.
- [ ] Preserve attempt, assignment, and status history across retries and reassignment.
- [ ] Implement resident and administrator status updates from collector actions.
- [ ] Build collector dashboard, group list, pickup detail, and completed-work views.
- [ ] Add tests for partial failures, invalid transitions, reassignment, and unauthorized updates.
- [ ] Verify the M6 exit gate.

### 14.8 M7 — Notifications, Analytics, and Reporting

- [ ] Define notification events, templates, persistence, and delivery states.
- [ ] Implement in-app notification creation, listing, and read/unread updates.
- [ ] Add reliable delivery handling and optional email delivery behind configuration.
- [ ] Implement collection history and administrator reporting queries.
- [ ] Add dashboard charts, filters, exports, and reconciliation checks.
- [ ] Build resident notification and administrator reporting views.
- [ ] Test notification idempotency, failures, permissions, and report accuracy.
- [ ] Verify the M7 exit gate.

### 14.9 M8 — Advanced Logistics and AI

- [ ] Confirm product, privacy, accuracy, and operational requirements for each advanced feature.
- [ ] Evaluate and approve PostGIS adoption before geographic persistence work.
- [ ] Implement GPS proximity clustering behind the grouping-service boundary.
- [ ] Add vehicle, capacity, route, stop, and route-optimization workflows.
- [ ] Add mapped stops and live-location updates with privacy controls.
- [ ] Add secure waste-photo upload, retention, and classification workflows.
- [ ] Add demand forecasting and sustainability analytics only with documented assumptions.
- [ ] Add accuracy, performance, privacy, and fallback tests for each advanced module.
- [ ] Verify the M8 exit gate before enabling advanced features for users.

### 14.10 M9 — Payments and External Integrations

- [ ] Confirm provider availability, credentials, documentation, pricing, and legal requirements.
- [ ] Define integration boundaries, webhook verification, retry, timeout, and reconciliation rules.
- [ ] Implement pricing, payment, receipt, refund, and invoice models where required.
- [ ] Implement payment-provider adapter and idempotent payment workflows.
- [ ] Implement smart-bin and government-system adapters behind explicit interfaces.
- [ ] Store credentials only in deployment secret management.
- [ ] Add external failure, replay, reconciliation, security, and contract tests.
- [ ] Verify the M9 exit gate for each integration independently.

### 14.11 M10 — Quality Assurance and Deployment

- [ ] Run the complete unit, integration, authorization, concurrency, frontend, and E2E suites.
- [ ] Execute performance tests for grouping, pagination, dashboards, and core APIs.
- [ ] Complete accessibility, responsive, browser, and usability checks.
- [ ] Perform security review, dependency checks, and sensitive-data verification.
- [ ] Rehearse Flyway migration, backup, restore, and rollback procedures.
- [ ] Configure staging and production environments, HTTPS, CORS, secrets, and access controls.
- [ ] Configure structured logging, health checks, monitoring, alerting, and retention.
- [ ] Complete release acceptance scenarios and obtain sign-off.
- [ ] Publish deployment and operations documentation.
- [ ] Verify the M10 exit gate and production-readiness decision.

### 14.12 Cross-Cutting Tasks

- [ ] Update API, architecture, database, security, testing, and deployment documentation with each relevant change.
- [ ] Add automated tests in the same vertical slice as every feature.
- [ ] Review authorization and sensitive-data handling for every endpoint and screen.
- [ ] Keep migrations backward-compatible with the deployment and rollback strategy.
- [ ] Maintain seed data and a repeatable local/staging reset process.
- [ ] Track technical debt, deferred decisions, operational risks, and external dependencies.
- [ ] Keep the task list and milestone status synchronized with the issue tracker.
