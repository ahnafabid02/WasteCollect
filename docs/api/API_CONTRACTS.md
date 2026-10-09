# WasteCollect API Contracts

## Conventions

- Base path: `/api/v1`
- JSON request and response bodies
- ISO-8601 timestamps with timezone
- UUIDs for internal references; public request codes for resident-facing confirmation
- Cursor or page-based pagination with stable sorting
- Consistent error shape:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [{ "field": "preferredDate", "reason": "must not be in the past" }],
  "requestId": "uuid"
}
```

## Endpoint groups

| Area | Routes |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Users | `GET /users/me`, `PATCH /users/me` |
| Catalog | `GET /zones`, `POST /admin/zones`, `GET /waste-categories` |
| Resident requests | `POST /requests`, `GET /requests/my`, `GET /requests/{id}`, `PATCH /requests/{id}/cancel` |
| Admin requests | `GET /admin/requests`, `GET /admin/requests/{id}` |
| Grouping | `POST /admin/groups/suggestions`, `POST /admin/groups` |
| Groups | `GET /admin/groups`, `PATCH /admin/groups/{id}`, `PATCH /admin/groups/{id}/schedule` |
| Collectors | `GET /admin/collectors`, `POST /admin/collectors`, `PATCH /admin/groups/{id}/assign` |
| Collector work | `GET /collector/groups`, `PATCH /collector/requests/{id}/status` |
| Notifications | `GET /notifications`, `PATCH /notifications/{id}/read` |
| Reporting | `GET /admin/dashboard/stats`, `GET /admin/reports/collections` |

## Contract requirements

Each route documents authentication, role, ownership scope, parameters, validation, response DTO, error responses, pagination, and idempotency behavior. State-changing actions that may be retried accept an idempotency key and return the original result for a duplicate key.

## M3 resident request slice

- `GET /api/v1/zones` and `GET /api/v1/waste-categories` are public and return active catalog entries.
- `POST /api/v1/requests` requires a resident access token. It accepts `zoneId`, `categoryId`, `address`, positive `quantity`, category-compatible `unit`, a current-or-future `preferredDate`, and optional `notes`.
- `GET /api/v1/requests/my` and `GET /api/v1/requests/{id}` are resident-owned reads; a request belonging to another resident is not disclosed.
- `PATCH /api/v1/requests/{id}/cancel` records a `CANCELLED` status-history event and is allowed only for the authenticated resident's eligible request.
