ALTER TABLE warehouse_environment_reading
    ADD COLUMN gas_tvoc_ppb DOUBLE NULL,
    ADD COLUMN risk_level INT NULL,
    ADD COLUMN confidence DOUBLE NULL,
    ADD COLUMN ai_status VARCHAR(32) NULL,
    ADD COLUMN action_code VARCHAR(64) NULL,
    ADD COLUMN model_version VARCHAR(64) NULL,
    ADD COLUMN prediction_status VARCHAR(64) NULL;
