CREATE TABLE app_users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'STAFF', 'VIEWER'))
);

CREATE TABLE campus_locations (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE waste_records (
    id CHAR(36) PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    subtype VARCHAR(120) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    collected_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0,
    handed_over_quantity DECIMAL(12, 3) NOT NULL DEFAULT 0,
    unit VARCHAR(10) NOT NULL,
    location_id CHAR(36) NOT NULL,
    recorded_by CHAR(36) NOT NULL,
    recorded_at DATE NOT NULL,
    segregation_status VARCHAR(24) NOT NULL DEFAULT 'NOT_VERIFIED',
    collection_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    demo BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_record_location FOREIGN KEY (location_id) REFERENCES campus_locations(id),
    CONSTRAINT fk_record_user FOREIGN KEY (recorded_by) REFERENCES app_users(id),
    CONSTRAINT chk_record_category CHECK (category IN ('PLASTIC', 'E_WASTE')),
    CONSTRAINT chk_record_unit CHECK (unit IN ('KG', 'ITEM')),
    CONSTRAINT chk_record_quantity CHECK (quantity > 0 AND collected_quantity >= 0 AND handed_over_quantity >= 0),
    CONSTRAINT chk_record_segregation CHECK (segregation_status IN ('NOT_VERIFIED', 'PROPER', 'NEEDS_CORRECTION')),
    CONSTRAINT chk_record_collection CHECK (collection_status IN ('PENDING', 'SCHEDULED', 'COLLECTED', 'HANDED_OVER')),
    INDEX idx_record_date (recorded_at),
    INDEX idx_record_category_status (category, collection_status),
    INDEX idx_record_location (location_id)
);

CREATE TABLE collection_history (
    id CHAR(36) PRIMARY KEY,
    record_id CHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    quantity DECIMAL(12, 3) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    performed_by CHAR(36) NOT NULL,
    recipient VARCHAR(160),
    notes VARCHAR(1000),
    CONSTRAINT fk_history_record FOREIGN KEY (record_id) REFERENCES waste_records(id),
    CONSTRAINT fk_history_user FOREIGN KEY (performed_by) REFERENCES app_users(id),
    CONSTRAINT chk_history_action CHECK (action IN ('COLLECTION', 'HANDOVER')),
    CONSTRAINT chk_history_quantity CHECK (quantity > 0),
    INDEX idx_history_record_time (record_id, occurred_at)
);

CREATE TABLE alerts (
    id CHAR(36) PRIMARY KEY,
    record_id CHAR(36),
    message VARCHAR(500) NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    resolved_by CHAR(36),
    CONSTRAINT fk_alert_record FOREIGN KEY (record_id) REFERENCES waste_records(id),
    CONSTRAINT fk_alert_user FOREIGN KEY (resolved_by) REFERENCES app_users(id),
    INDEX idx_alert_open (resolved, created_at)
);

CREATE TABLE audit_logs (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id CHAR(36),
    details VARCHAR(1000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES app_users(id),
    INDEX idx_audit_time (occurred_at),
    INDEX idx_audit_entity (entity_type, entity_id)
);
