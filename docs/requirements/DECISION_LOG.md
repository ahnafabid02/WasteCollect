# WasteCollect M0 Decision Log

| ID | Decision | Owner | Status | Revisit trigger |
|---|---|---|---|---|
| D-001 | Use a modular monolith with Spring Boot, React, and PostgreSQL. | Technical lead | Approved | Operational scale requires extraction |
| D-002 | Group initially by controlled `service_zone_id` and preferred date. | Product/technical lead | Approved | GPS or capacity requirements are approved |
| D-003 | Use UUID internal IDs and separate human-readable request codes. | Backend lead | Approved | External identifier requirements change |
| D-004 | Use short-lived JWT access tokens and rotated, revocable refresh sessions. | Security owner | Approved | Identity provider adoption |
| D-005 | Backend is the authority for authorization; frontend guards are UX only. | Security owner | Approved | Architecture changes |
| D-006 | Suggestions do not persist memberships; confirmation is transactional. | Operations owner | Approved | Grouping workflow changes |
| D-007 | Keep status history, membership history, assignment history, and pickup attempts. | Data owner | Approved | Retention/legal requirements change |
| D-008 | Use Flyway for every schema change. | Backend lead | Approved | Migration tooling changes |
| D-009 | Use `/api/v1`, ISO-8601 timestamps with timezone, consistent pagination, and structured errors. | API owner | Approved | API versioning policy changes |
| D-010 | Advanced logistics, AI, payments, IoT, and government integration are deferred. | Product owner | Approved | Provider and business requirements are ready |

## Assumptions

- Initial service zones are configured by administrators.
- Pickup quantities use a category-approved unit.
- Resident addresses and contact details are sensitive data.
- Email delivery is optional; in-app notifications are mandatory for defined events.
- The initial release operates in one deployment region and one primary timezone configured by the deployment.

## Open decisions

No M1-blocking decisions remain. Non-blocking choices, such as the first hosting provider and optional email provider, are resolved before the affected milestone.
