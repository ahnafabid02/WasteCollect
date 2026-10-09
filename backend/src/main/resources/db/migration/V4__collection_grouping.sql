ALTER TABLE pickup_requests DROP CONSTRAINT pickup_requests_status_check;
ALTER TABLE pickup_requests ADD CONSTRAINT pickup_requests_status_check
    CHECK (status IN ('PENDING', 'GROUPED', 'SCHEDULED', 'IN_PROGRESS', 'FAILED', 'CANCELLED', 'COMPLETED'));

CREATE TABLE collection_groups (
    id UUID PRIMARY KEY,
    public_code VARCHAR(24) NOT NULL UNIQUE,
    service_zone_id UUID NOT NULL REFERENCES service_zones(id),
    preferred_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    created_by UUID NOT NULL REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE group_memberships (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES collection_groups(id),
    pickup_request_id UUID NOT NULL REFERENCES pickup_requests(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    added_by UUID NOT NULL REFERENCES app_users(id),
    added_at TIMESTAMPTZ NOT NULL,
    removed_by UUID REFERENCES app_users(id),
    removed_at TIMESTAMPTZ,
    CONSTRAINT uq_group_request UNIQUE (group_id, pickup_request_id),
    CONSTRAINT ck_membership_removal CHECK (
        (active AND removed_by IS NULL AND removed_at IS NULL) OR
        (NOT active AND removed_by IS NOT NULL AND removed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_active_membership_per_request
    ON group_memberships(pickup_request_id) WHERE active;

CREATE TABLE group_membership_history (
    id UUID PRIMARY KEY,
    membership_id UUID NOT NULL REFERENCES group_memberships(id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('ADDED', 'REMOVED')),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    details VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pickup_grouping_eligibility
    ON pickup_requests(service_zone_id, preferred_date, status);
CREATE INDEX idx_collection_groups_zone_date
    ON collection_groups(service_zone_id, preferred_date, status);
CREATE INDEX idx_group_memberships_group_active
    ON group_memberships(group_id, active);
CREATE INDEX idx_group_membership_history_membership
    ON group_membership_history(membership_id, created_at);
CREATE INDEX idx_audit_logs_entity
    ON audit_logs(entity_type, entity_id, created_at);
