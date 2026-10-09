CREATE TABLE pickup_attempts (
    id UUID PRIMARY KEY,
    pickup_request_id UUID NOT NULL REFERENCES pickup_requests(id),
    group_id UUID NOT NULL REFERENCES collection_groups(id),
    assignment_id UUID NOT NULL REFERENCES collector_assignments(id),
    collector_id UUID NOT NULL REFERENCES app_users(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('COMPLETED', 'FAILED')),
    reason VARCHAR(500) NOT NULL,
    retry_at TIMESTAMPTZ,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pickup_attempt_number UNIQUE (pickup_request_id, attempt_number)
);

CREATE INDEX idx_pickup_attempts_request ON pickup_attempts(pickup_request_id, attempted_at);
CREATE INDEX idx_pickup_attempts_group ON pickup_attempts(group_id, attempted_at);
