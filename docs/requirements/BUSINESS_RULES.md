# WasteCollect Business Rules

## Service areas and requests

- Requests reference a controlled service zone; free-text zone names are not used for grouping.
- A preferred pickup date must be today or later in the configured service timezone.
- Quantity is a positive decimal plus a permitted unit for the selected waste category.
- A request receives a unique public confirmation code separate from its internal UUID.
- A resident can read and cancel only their own eligible requests.

## Pickup request lifecycle

| Current | Allowed next states | Actor |
|---|---|---|
| `PENDING` | `GROUPED`, `CANCELLED` | System/admin, resident for cancellation |
| `GROUPED` | `SCHEDULED`, `PENDING` when a group plan is cancelled, `CANCELLED` | Admin/system, authorized admin cancellation |
| `SCHEDULED` | `IN_PROGRESS`, `PENDING` when a group plan is cancelled, `CANCELLED` only before cutoff | Collector/admin; individual scheduled-request cancellation is a later workflow |
| `IN_PROGRESS` | `COMPLETED`, `FAILED` | Assigned collector/admin override |
| `FAILED` | `PENDING`, `SCHEDULED`, terminal `FAILED` | Authorized admin according to retry policy |
| `COMPLETED` | none | — |
| `CANCELLED` | none | — |

Every transition is validated, records the actor/reason/time, and creates a history record.

## Collection-group lifecycle

| Current | Allowed next states | Rule |
|---|---|---|
| `DRAFT` | `SCHEDULED`, `CANCELLED` | Draft has no committed operational work |
| `SCHEDULED` | `IN_PROGRESS`, `CANCELLED` | Cancellation requires reassessing members |
| `IN_PROGRESS` | `COMPLETED`, `CANCELLED` | Completion follows member outcome policy |
| `COMPLETED` | none | Terminal |
| `CANCELLED` | none | Terminal |

## Grouping

1. Select requests that are `PENDING`, eligible, unassigned, and valid for grouping.
2. Partition by `(service_zone_id, preferred_date)`.
3. Return suggestions without persistence.
4. Allow administrator review and adjustment.
5. Revalidate every request inside a transaction during confirmation.
6. Enforce at most one active membership per request.
7. Move confirmed requests to `GROUPED` and record audit/history events.

## Scheduling and assignment

- Scheduled date must be valid for the service zone and must not violate cancellation or operational cutoffs.
- Rescheduling revalidates members and records the reason.
- A collector cannot have overlapping assignments when the configured availability rule disallows them.
- Reassignment closes the prior assignment record and creates a new assignment record.

### Implemented M5 policy

- Service dates use `Asia/Dhaka`; current service zones operate every day. Scheduling requires an active zone and cannot precede the group's preferred date.
- A collection window must have a future start, a later end, at most 24 hours duration, and the configured minimum notice (0–168 hours, default 0).
- Group size is an administrator setting (1–500, default 50), limited by the deployment ceiling.
- One active assignment per group is permitted. Collector time windows are half-open: overlap is rejected, but back-to-back windows are allowed.
- Rescheduling an assigned group closes the old assignment window and appends a new one for the same collector; reassignment preserves the old collector/window record.
- Administrative group cancellation before the window starts returns active requests to `PENDING`, releases memberships and assignments, and records the reason/history. This is cancellation of the collection plan, not cancellation of the resident's request.
- Resident self-cancellation remains limited to `PENDING` requests. Individual scheduled-request cancellation and work execution belong to later operational workflows.
- Group start/completion and pickup outcomes are M6 operations. M5 cannot alter a group once its operational window has started.

## Failure and retry

- A failed pickup requires a structured reason and optional notes.
- Each attempt is preserved; retries never overwrite previous attempts.
- Retry may return a request to `PENDING` or create a new scheduled action according to administrator policy.
- A group is complete only when every member reaches a terminal outcome or an administrator records an approved partial-completion decision.

## Notifications and reports

Persist notifications for request creation, grouping, scheduling, assignment, cancellation, completion, and failure.
Reports use authoritative request, group, assignment, and attempt records; derived totals must be reproducible from those records.
