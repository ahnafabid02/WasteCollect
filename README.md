# WasteCollect

WasteCollect is a waste collection management platform for residents, administrators, and collectors.

Implemented through M5: resident requests and tracking, administrator grouping, scheduling/rescheduling,
collector creation and assignment/reassignment, searchable operational lists, dashboard metrics,
audit history, and persistent operational settings. Collector work execution is the next milestone (M6).

## Repository structure

- `frontend/` — React, TypeScript, and Vite application
- `backend/` — Spring Boot REST API
- `infrastructure/` — local PostgreSQL and supporting services
- `docs/` — product, architecture, API, database, security, testing, and task documentation

## Prerequisites

- Node.js 20 or newer and npm
- Java 21 and Maven 3.9 or newer
- PostgreSQL 16 (a local Windows installation is supported)

## Local setup

1. Open PowerShell and prepare the local PostgreSQL role and database:

   ```powershell
   .\scripts\setup-local-postgres.ps1
   ```

   Enter the local PostgreSQL administrator password when prompted. The script creates or repairs
   only the project-specific role and database; it does not change PostgreSQL authentication
   rules or stop the service.

2. If you override the defaults, set the matching `DATABASE_URL`, `DATABASE_USERNAME`, and
   `DATABASE_PASSWORD` environment variables shown in `.env.example`.

   To provision the first administrator, set `APP_BOOTSTRAP_ADMIN_EMAIL` and an
   `APP_BOOTSTRAP_ADMIN_PASSWORD` of at least 12 characters before the first backend start.

3. Start the backend:

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

4. Start the frontend in a second terminal:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

5. Open `http://localhost:5173`.
6. Check backend health at `http://localhost:8080/actuator/health`.

## Administrator workflow

Sign in with a provisioned administrator account to open `/admin`. Use Requests to inspect resident
requests, Grouping to confirm zone/date suggestions, and Scheduling to set collection windows and
assign collectors. Collector accounts are created in Collectors; assignment conflicts are validated
before saving. Settings controls group size and scheduling notice. Audit history records operational
changes and each group preserves its previous assignments.

Local development and CI use native PostgreSQL. Backend startup applies Flyway migrations V1–V5.
`GROUPING_MAX_REQUESTS` is a deployment ceiling (default 500); the administrator setting defaults to
50 and cannot exceed that ceiling. Scheduling uses the service timezone `Asia/Dhaka`; the UI's time
inputs use the browser's local timezone and send ISO timestamps with an offset.

## Quality checks

```powershell
npm run lint --prefix frontend
npm run typecheck --prefix frontend
npm run test --prefix frontend
npm run build --prefix frontend
cd backend
.\mvnw.cmd verify
```

If Maven is not installed locally, run the backend through the CI environment or install Maven 3.9+ before starting it.

## Documentation

- [Development plan](./WasteCollect_Development_Plan.md)
- [Task list](./WasteCollect_Task_List.md)
- [Task descriptions](./docs/task-descriptions/README.md)
- [Implementation roadmap](./docs/roadmap/IMPLEMENTATION_ROADMAP.md)
