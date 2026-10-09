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

## Endpoint groups (implemented routes and later target contracts)

| Area | Routes |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Users | `GET /users/me`, `PATCH /users/me` |
| Catalog | `GET /zones`, `POST /admin/zones`, `GET /waste-categories` |
| Resident requests | `POST /requests`, `GET /requests/my`, `GET /requests/{id}`, `PATCH /requests/{id}/cancel` |
| Admin requests | `GET /admin/requests`, `GET /admin/requests/{id}` |
| Grouping | `POST /admin/groups/suggestions`, `POST /admin/groups` |
| Groups | `GET /admin/groups`, `PATCH /admin/groups/{id}`, `PATCH /admin/groups/{id}/schedule` |
| Collectors | `GET /admin/collectors`, `POST /admin/users/collectors`, `POST /admin/groups/{id}/assignment` |
| Collector work | `GET /collector/groups`, `PATCH /collector/requests/{id}/status` |
| Notifications | `GET /notifications`, `PATCH /notifications/{id}/read` |
| Reporting | `GET /admin/dashboard` (M5), `GET /admin/reports/collections` (M7 target) |

## Contract requirements

Each route documents authentication, role, ownership scope, parameters, validation, response DTO, error responses, pagination, and idempotency behavior. Dedicated idempotency keys remain a release-hardening target; implemented duplicate behavior is documented per slice below.

## M3 resident request slice

- `GET /api/v1/zones` and `GET /api/v1/waste-categories` are public and return active catalog entries.
- `POST /api/v1/requests` requires a resident access token. It accepts `zoneId`, `categoryId`, `address`, positive `quantity`, category-compatible `unit`, a current-or-future `preferredDate`, and optional `notes`.
- `GET /api/v1/requests/my` and `GET /api/v1/requests/{id}` are resident-owned reads; a request belonging to another resident is not disclosed.
- `PATCH /api/v1/requests/{id}/cancel` records a `CANCELLED` status-history event and is allowed only for the authenticated resident's eligible request.
- `GET /api/v1/requests/{id}/history` returns the authenticated resident's append-only status history.

## M4 grouping slice

- `POST /api/v1/admin/groups/suggestions` is admin-only and returns non-persistent partitions of eligible pending requests by `(zoneId, preferredDate)`.
- `POST /api/v1/admin/groups` accepts an administrator-adjusted `zoneId`, `preferredDate`, and unique `requestIds`. It locks and revalidates requests, creates the group and memberships, records history/audit entries, and changes requests to `GROUPED` atomically.
- `GET /api/v1/admin/groups` returns confirmed groups and active members for administrator review.
- Conflicting or repeated confirmation returns `409 CONFLICT`; validation failures return the documented structured error response.

## M5 administrator operations

All routes below require an active `ADMIN` account and a bearer access token. Resident and collector
tokens are denied. Unknown record IDs return 404; validation failures return 400; lifecycle,
availability, and uniqueness conflicts return 409. Successful mutations return 204 unless specified.

| Method | Route (after `/api/v1/admin`) | Contract |
|---|---|---|
| GET | `/requests` | Search resident requests; response `{items,total,page,size}` |
| GET | `/requests/{id}` | Request details and append-only `history` |
| GET | `/collections` | Search confirmed groups with schedule, active member count, and collector |
| PATCH | `/groups/{id}/schedule` | `{startsAt,endsAt,reason}`; schedule or reschedule |
| POST | `/groups/{id}/assignment` | `{collectorId,reason}`; assign or reassign |
| PATCH | `/groups/{id}/cancel` | `{reason}`; cancel upcoming group and release its members to `PENDING` |
| GET | `/groups/{id}/assignments` | Historical windows/collectors; `closedAt:null` identifies the current assignment |
| GET | `/collectors` | Collector names, email, status, and active assignment totals |
| GET | `/collectors/availability` | `startsAt`, `endsAt`, optional `groupId`; active collectors without overlapping work |
| POST | `/users/collectors` | `{email,displayName,temporaryPassword}`; 201 with collector profile |
| PATCH | `/collectors/{id}/status` | `{status:"ACTIVE"\|"SUSPENDED"}`; active work must first be reassigned |
| GET | `/dashboard` | Authoritative request/group counts by status, active collector count, unassigned scheduled count |
| GET | `/audit` | Optional `entityId`, `page`, `size`; paginated actor/action/entity/reason/time history |
| GET | `/settings` | `{maxGroupRequests,minimumNoticeHours,serviceTimezone}` |
| PATCH | `/settings` | `{maxGroupRequests,minimumNoticeHours}`; 200 with persisted settings |

Search parameters: `query` (case-insensitive code/zone and request address/resident), `status`,
`zoneId`, zero-based `page` (default 0), `size` (default 20, maximum 100),
`sort` (`createdAt`, `preferredDate`, `status`, `publicCode`), and `direction` (`asc` or `desc`).
Sort input is allowlisted; UUID tie-breaking makes pagination deterministic.

Schedule timestamps must include a timezone, start no earlier than the preferred service date,
satisfy the configured notice period, and describe a positive window no longer than 24 hours.
Only `DRAFT`/`SCHEDULED` groups with eligible members can be scheduled. Existing windows cannot be
rescheduled, reassigned, or cancelled after they start. Availability previews are advisory; mutations
recheck availability under locks. Adjacent windows are permitted. Retrying the same schedule or
current collector assignment is a no-op; duplicate collector creation is rejected and repeat group
cancellation returns 409. No mutation overwrites historical assignment rows.
