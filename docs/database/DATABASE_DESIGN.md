# WasteCollect Database Design

## Core tables

`users`, `service_zones`, `waste_categories`, `pickup_requests`, `collection_groups`, `group_memberships`, `collector_assignments`, `pickup_attempts`, `pickup_status_history`, `notifications`, `audit_logs`, and `refresh_sessions`.

## Required invariants

- User email and public request code are unique.
- Every request references an existing resident, service zone, and waste category.
- Quantity is positive and paired with a permitted unit.
- Internal identifiers are UUIDs.
- A request has at most one active group membership.
- Status history is append-only.
- Assignment history is append-only; only one active assignment is allowed where policy requires it.
- Foreign keys prevent orphaned operational records.
- Zone/date/status indexes support grouping and administrator queries.

## Transaction boundaries

- Confirming a group locks or rechecks candidate requests, creates the group and memberships, updates request status, and writes audit/history records atomically.
- Scheduling validates group state and date before changing the group and member requests.
- Assignment validates collector availability and writes the assignment and audit record atomically.
- Status changes validate the transition and append history in one transaction.

## Implemented through M5

- `V1` establishes the migration baseline, `V2` identity and sessions, `V3` request/catalog persistence, and `V4` grouping/audit persistence.
- `uq_active_membership_per_request` is a PostgreSQL partial unique index over active memberships.
- Group confirmation uses pessimistic request-row locking plus the database uniqueness constraint as defense in depth.
- Group membership and request status histories are append-only; administrator confirmation also writes an audit event.
- `V5` adds group schedule timestamps, `collector_assignments`, and singleton `operation_settings`, with scheduling/availability/audit indexes.
- `uq_active_assignment_per_group` guarantees one current collector assignment per group. Closed rows preserve their original collector and time window.
- Scheduling/cancellation locks the group and active member rows. Assignment/reassignment locks the group and old/new collector rows in UUID order; overlap checks run after the collector lock is acquired. All application assignment writers use this path.
- Cancelling an upcoming group closes its assignment, deactivates memberships, appends membership/request history, returns members to `PENDING`, and writes an audit event atomically.
- Administrator search uses parameterized SQL, allowlisted sorting, stable UUID tie-breaking, and bounded page sizes. Dashboard totals are computed from source tables.

## Retention

Operational history is retained for reporting and audit requirements. Sensitive address, contact, and location data must have an approved retention period before production launch. Migrations are versioned through Flyway and never applied by silent schema mutation.

## Deferred schema

Vehicle, route, route-stop, live-location, waste-photo, classification, payment, smart-bin, and external-sync tables are introduced only with their approved module designs.
