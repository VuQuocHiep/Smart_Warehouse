-- Treat the imported opening history and subsequent transactions as one live ledger.
-- Keep V12-V15 unchanged so already migrated databases retain valid Flyway checksums.
CREATE TEMPORARY TABLE demand_series_unified AS
SELECT warehouse_id, product_id, MIN(history_start) AS history_start,
       MIN(seed_batch_id) AS seed_batch_id
FROM demand_series
GROUP BY warehouse_id, product_id;

START TRANSACTION;

UPDATE stock_transaction SET data_source='REAL' WHERE data_source='SIMULATED';

DELETE FROM demand_series;
INSERT INTO demand_series (warehouse_id,product_id,data_source,history_start,complete_through,seed_batch_id)
SELECT warehouse_id,product_id,'REAL',history_start,NULL,seed_batch_id
FROM demand_series_unified;

-- Old jobs/models describe a different dataset; never relabel serialized joblib files.
UPDATE demand_job
SET status='FAILED', attempts=3, message='History unified; replaced by a new REAL training job',
    claim_token=NULL, claim_until=NULL, snapshot_json=NULL, updated_at=UTC_TIMESTAMP(6)
WHERE status IN ('QUEUED','RUNNING');
UPDATE demand_cloud_state
SET active_model_version=NULL, active_result_job_id=NULL, last_dispatch_at=NULL
WHERE id=1;

COMMIT;
DROP TEMPORARY TABLE demand_series_unified;
