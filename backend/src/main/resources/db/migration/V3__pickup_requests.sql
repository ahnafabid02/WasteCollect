CREATE TABLE service_zones (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE waste_categories (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL UNIQUE,
    allowed_unit VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE pickup_requests (
    id UUID PRIMARY KEY,
    public_code VARCHAR(24) NOT NULL UNIQUE,
    resident_id UUID NOT NULL REFERENCES app_users(id),
    service_zone_id UUID NOT NULL REFERENCES service_zones(id),
    waste_category_id UUID NOT NULL REFERENCES waste_categories(id),
    address VARCHAR(300) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(20) NOT NULL,
    preferred_date DATE NOT NULL,
    notes VARCHAR(1000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'GROUPED', 'SCHEDULED', 'CANCELLED', 'COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE pickup_status_history (
    id UUID PRIMARY KEY,
    pickup_request_id UUID NOT NULL REFERENCES pickup_requests(id),
    previous_status VARCHAR(20),
    next_status VARCHAR(20) NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_users(id),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO service_zones (id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000001', 'CENTRAL', 'Central Zone'),
    ('00000000-0000-0000-0000-000000000002', 'NORTH', 'North Zone')
ON CONFLICT DO NOTHING;

INSERT INTO waste_categories (id, code, name, allowed_unit) VALUES
    ('00000000-0000-0000-0000-000000000001', 'GENERAL', 'General waste', 'BAG'),
    ('00000000-0000-0000-0000-000000000002', 'RECYCLING', 'Recyclables', 'BAG'),
    ('00000000-0000-0000-0000-000000000003', 'ORGANIC', 'Organic waste', 'KG')
ON CONFLICT DO NOTHING;

CREATE INDEX idx_pickup_requests_resident ON pickup_requests(resident_id, created_at DESC);
CREATE INDEX idx_pickup_requests_status_date ON pickup_requests(status, preferred_date);
