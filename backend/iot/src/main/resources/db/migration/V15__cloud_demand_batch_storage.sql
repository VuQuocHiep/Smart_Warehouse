-- Cloud batch state is durable even when Render's filesystem is replaced.
ALTER TABLE demand_job
    ADD COLUMN claim_token VARCHAR(64) NULL,
    ADD COLUMN claim_until DATETIME(6) NULL,
    ADD COLUMN runner_id VARCHAR(128) NULL,
    ADD COLUMN snapshot_json MEDIUMTEXT NULL;

CREATE TABLE demand_model_artifact (
    model_version VARCHAR(128) PRIMARY KEY,
    training_job_id VARCHAR(36) NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    data_source VARCHAR(16) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    metadata_json TEXT NOT NULL,
    artifact MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE demand_forecast_result (
    job_id VARCHAR(36) PRIMARY KEY,
    model_version VARCHAR(128) NOT NULL,
    forecast_date DATE NOT NULL,
    result_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_demand_result_date (forecast_date, created_at)
) ENGINE=InnoDB;

CREATE TABLE demand_cloud_state (
    id INT PRIMARY KEY,
    active_model_version VARCHAR(128) NULL,
    active_result_job_id VARCHAR(36) NULL,
    last_dispatch_at DATETIME(6) NULL
) ENGINE=InnoDB;
INSERT INTO demand_cloud_state (id) VALUES (1);
