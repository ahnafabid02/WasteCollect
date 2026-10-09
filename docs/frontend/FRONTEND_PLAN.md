# WasteCollect Frontend Plan

## Routes

- Public: `/`, `/how-it-works`, `/waste-information`, `/register`, `/login`
- Resident: `/resident`, `/resident/requests/new`, `/resident/requests/:id`, `/resident/profile`, `/resident/notifications`
- Administrator: `/admin`, `/admin/requests`, `/admin/groups`, `/admin/scheduling`, `/admin/collectors`, `/admin/reports`, `/admin/settings`
- Collector: `/collector`, `/collector/groups`, `/collector/tasks`, `/collector/requests/:id`, `/collector/history`, `/collector/profile`

## Implemented routes through M5

- Public: `/`, `/how-it-works`, `/waste-information`, `/register`, `/login`
- Resident: `/account` and `/requests/:id` for request creation, status, history, and pending-request cancellation
- Administrator: `/admin/groups` for zone/date suggestions, manual membership adjustment, confirmation, and confirmed-group review
- Administrator: `/admin` overview, `/admin/requests` search/detail/history, `/admin/scheduling` search/schedule/reschedule/assign/reassign/cancel, `/admin/collectors` creation and activation/suspension, `/admin/audit` paginated operational history, and `/admin/settings` persisted operational settings

The broader route map above remains the later target. Administrator sign-in opens `/admin`; resident sign-in opens `/account`. Sign-out revokes the refresh session and always clears local state even if the service is unavailable. Administrator data loads have timeouts and abort obsolete loads when filters/pages change. Forms retain conflict errors, show success states, and refresh persisted data after changes.

## Structure

Use feature-first folders: `app`, `components`, `features/auth`, `features/resident`, `features/admin`, `features/collector`, `hooks`, `services`, `types`, and `utils`.

The M5 administrator feature lives in `features/admin` with typed API contracts, native form validation, and controlled loading/error/retry state. TanStack Query and dedicated form libraries remain targets for a later shared-client refactor. Route guards improve navigation but do not provide security.

## Required UX states

Every data-backed screen has loading, empty, validation-error, API-error, unauthorized, success, and retry states. Forms are keyboard accessible, responsive, and clear about dates, quantities, status, and irreversible actions.

## Design-system baseline

Define typography, color tokens, spacing, cards, tables, forms, buttons, status indicators, alerts, dialogs, navigation, and responsive breakpoints before building role-specific pages.
