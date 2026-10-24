ALTER TABLE alerts
    ADD COLUMN alert_type VARCHAR(32) NOT NULL DEFAULT 'COLLECTION_OVERDUE',
    ADD INDEX idx_alert_record_type_resolved (record_id, alert_type, resolved),
    ADD CONSTRAINT chk_alert_type CHECK (alert_type IN (
        'COLLECTION_OVERDUE', 'COLLECTION_STATUS_STALE', 'SEGREGATION_CORRECTION',
        'E_WASTE_HANDOVER_OVERDUE', 'MISSING_REQUIRED_INFO'
    ));

CREATE TABLE e_waste_items (
    id CHAR(36) PRIMARY KEY,
    record_id CHAR(36) NOT NULL UNIQUE,
    item_type VARCHAR(120) NOT NULL,
    item_condition VARCHAR(20) NOT NULL,
    storage_location VARCHAR(160) NOT NULL,
    handover_date DATE NULL,
    authorized_recipient VARCHAR(160) NULL,
    CONSTRAINT fk_ewaste_record FOREIGN KEY (record_id) REFERENCES waste_records(id),
    CONSTRAINT chk_ewaste_condition CHECK (item_condition IN ('FUNCTIONAL', 'DAMAGED', 'UNUSABLE')),
    INDEX idx_ewaste_type_condition (item_type, item_condition)
);
