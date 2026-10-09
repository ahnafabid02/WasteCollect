# WasteCollect Security Design

## Identity and sessions

- Residents self-register.
- Administrators are provisioned securely.
- Administrators create or invite collectors.
- Passwords are stored only as strong one-way hashes.
- Access tokens are short-lived.
- Refresh sessions are stored as hashed references, rotated, revocable, and expiry-limited.
- Logout revokes the refresh session.

## Authorization

| Capability | Resident | Administrator | Collector |
|---|---:|---:|---:|
| Create own request | Yes | No by default | No |
| Read own request | Yes | Yes | Assigned only |
| Cancel eligible request | Yes | Authorized override | No |
| Confirm groups/schedule | No | Yes | No |
| Assign/reassign collectors | No | Yes | No |
| Update pickup outcome | No | Authorized override | Assigned only |
| Read system reports | No | Yes | No |

Every endpoint checks both role permission and record ownership/assignment. A frontend route guard never replaces backend authorization.

## Sensitive data

Addresses, contact details, credentials, refresh references, audit context, and live collector coordinates are sensitive. Logs must avoid tokens, passwords, and unnecessary personal data. Data access follows least privilege.

## Security controls

- HTTPS in deployed environments.
- Restricted CORS and secure cookie/token configuration.
- Input validation and structured errors without sensitive details.
- Rate limiting and abuse protection for authentication endpoints.
- Audit records for administrator overrides, grouping, scheduling, assignment, and status changes.
- Dependency, secret, authorization, and negative-path tests in CI.

## Implemented identity provisioning

- Residents self-register with a minimum 12-character password that is BCrypt-hashed.
- A local/initial administrator is created only when explicit `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD` values are supplied; no administrator password is committed.
- Collector creation is restricted to `ADMIN` and accepts a temporary password meeting the same minimum length.
- Both backend authorization and record-level resident ownership checks are covered by integration tests.

## M5 operational controls

- Every `/api/v1/admin/**` read and mutation requires `ADMIN`; resident and collector denial paths are integration-tested.
- Only active collectors can receive work. A collector with active assignments cannot be suspended; reassignment/cancellation must release that work first.
- Schedules, reassignments, cancellations, collector creation/status changes, and settings changes write actor-aware audit records in their transaction.
- Administrator search binds data parameters and only permits known sort columns/directions.
- Request cancellation and group confirmation serialize on the request row so cancellation cannot orphan a confirmed membership.
