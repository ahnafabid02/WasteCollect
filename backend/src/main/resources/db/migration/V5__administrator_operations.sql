ALTER TABLE collection_groups ADD COLUMN scheduled_start TIMESTAMPTZ;
ALTER TABLE collection_groups ADD COLUMN scheduled_end TIMESTAMPTZ;
ALTER TABLE collection_groups ADD CONSTRAINT ck_group_schedule CHECK (
    (scheduled_start IS NULL AND scheduled_end IS NULL) OR scheduled_end > scheduled_start
);

CREATE TABLE collector_assignments (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES collection_groups(id),
    collector_id UUID NOT NULL REFERENCES app_users(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
    assigned_by UUID NOT NULL REFERENCES app_users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL
);
CREATE UNIQUE INDEX uq_active_assignment_per_group ON collector_assignments(group_id) WHERE closed_at IS NULL;
CREATE INDEX idx_collector_availability ON collector_assignments(collector_id, starts_at, ends_at) WHERE closed_at IS NULL;
CREATE TABLE operation_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    max_group_requests INTEGER NOT NULL CHECK (max_group_requests BETWEEN 1 AND 500),
    minimum_notice_hours INTEGER NOT NULL CHECK (minimum_notice_hours BETWEEN 0 AND 168),
    service_timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Dhaka'
);
INSERT INTO operation_settings(id, max_group_requests, minimum_notice_hours) VALUES (1, 50, 0);
CREATE INDEX idx_groups_schedule ON collection_groups(scheduled_start, status);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
