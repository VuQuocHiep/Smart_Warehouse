ALTER TABLE warehouse_environment_reading
    ADD COLUMN ai_risk_level INT NULL,
    ADD COLUMN gas_risk_level INT NULL,
    ADD COLUMN decision_source VARCHAR(32) NULL;
