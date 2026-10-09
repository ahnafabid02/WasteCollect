# WasteCollect Feature Inventory

**Status:** Approved for M1 foundation
**Scope:** M0 core definition

## Core release

### Public and resident features

- Public landing page, service explanation, and waste-category information.
- Resident registration, login, logout, refresh session, and profile management.
- Pickup request creation with category, numeric quantity/unit, address, service zone, preferred date, and notes.
- Resident request list, detail, status history, confirmation code, tracking, and eligible cancellation.
- In-app notifications and collection history.

### Administrator features

- Service-zone and waste-category administration.
- Searchable and filterable request list.
- Grouping suggestions, manual review, adjustment, and atomic confirmation.
- Group scheduling, rescheduling, lifecycle management, collector assignment, and reassignment.
- Collector account management.
- Dashboard metrics, audit activity, and collection reports.

### Collector features

- Assigned-group list and daily worklist.
- Assigned request details and pickup addresses.
- Start work, record pickup attempts, complete, fail, and retry eligible work.
- Assigned-work history.

### Platform capabilities

- Backend-enforced authentication, RBAC, and record ownership.
- Request/group status validation and immutable status history.
- PostgreSQL persistence with Flyway migrations and integrity constraints.
- OpenAPI contract under `/api/v1`.
- Audit records for sensitive operational changes.
- Automated unit, integration, authorization, concurrency, frontend, and E2E tests.

## Deferred roadmap

GPS clustering, PostGIS, route optimization, live collector tracking, image classification, demand forecasting, sustainability analytics, payments, smart-bin IoT, and government integrations remain planned but are not M1 blockers.

## Scope boundary

M1–M7 deliver the core platform. M8–M9 are optional extensions. No deferred feature may change core identity, ownership, or pickup lifecycle rules without an approved design update.
