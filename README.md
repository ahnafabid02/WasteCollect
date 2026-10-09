# WasteCollect

WasteCollect is a waste collection management platform for residents, administrators, and collectors.

## Repository structure

- `frontend/` — React, TypeScript, and Vite application
- `backend/` — Spring Boot REST API
- `infrastructure/` — local PostgreSQL and supporting services
- `docs/` — product, architecture, API, database, security, testing, and task documentation

## Prerequisites

- Node.js 20 or newer and npm
- Java 21 and Maven 3.9 or newer
- Docker Desktop with Docker Compose

## Local setup

1. Copy `.env.example` to `.env` and review the local values.
2. Start PostgreSQL:

   ```powershell
   docker compose -f infrastructure/docker-compose.yml up -d postgres
   ```

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
- [M1 roadmap](./docs/roadmap/IMPLEMENTATION_ROADMAP.md)
