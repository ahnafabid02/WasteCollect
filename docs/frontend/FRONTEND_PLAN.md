# WasteCollect Frontend Plan

## Routes

- Public: `/`, `/how-it-works`, `/waste-information`, `/register`, `/login`
- Resident: `/resident`, `/resident/requests/new`, `/resident/requests/:id`, `/resident/profile`, `/resident/notifications`
- Administrator: `/admin`, `/admin/requests`, `/admin/groups`, `/admin/scheduling`, `/admin/collectors`, `/admin/reports`, `/admin/settings`
- Collector: `/collector`, `/collector/groups`, `/collector/tasks`, `/collector/requests/:id`, `/collector/history`, `/collector/profile`

## Implemented routes through M4

- Public: `/`, `/how-it-works`, `/waste-information`, `/register`, `/login`
- Resident: `/account` and `/requests/:id` for request creation, status, history, and pending-request cancellation
- Administrator: `/admin/groups` for zone/date suggestions, manual membership adjustment, confirmation, and confirmed-group review

The broader route map above remains the post-M4 target. Authentication currently routes each implemented role directly to its meaningful workspace, and sign-out revokes the refresh session before clearing browser state.

## Structure

Use feature-first folders: `app`, `components`, `features/auth`, `features/resident`, `features/admin`, `features/collector`, `hooks`, `services`, `types`, and `utils`.

Typed API clients and TanStack Query own server state. React Hook Form and Zod handle form state and client validation. Route guards improve navigation but do not provide security.

## Required UX states

Every data-backed screen has loading, empty, validation-error, API-error, unauthorized, success, and retry states. Forms are keyboard accessible, responsive, and clear about dates, quantities, status, and irreversible actions.

## Design-system baseline

Define typography, color tokens, spacing, cards, tables, forms, buttons, status indicators, alerts, dialogs, navigation, and responsive breakpoints before building role-specific pages.
