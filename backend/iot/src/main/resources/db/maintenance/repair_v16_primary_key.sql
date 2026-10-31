-- Run manually against the intended application database after deploying the V16 PK fix.
-- This is NOT an automatic migration. It only recognizes the original V16 checksum.
-- Cloud: original V16 failed at its first CREATE TEMPORARY TABLE with error 3750.
-- Local: original V16 succeeded; only the temporary table definition changed.
-- No business tables or other Flyway versions are modified.
SELECT DATABASE() AS database_name;
SELECT version,script,checksum,success FROM flyway_schema_history WHERE version='16';

START TRANSACTION;
DELETE FROM flyway_schema_history
WHERE version='16' AND type='SQL'
  AND script='V16__unify_stock_history_as_real.sql'
  AND checksum=725522090 AND success=0;

UPDATE flyway_schema_history SET checksum=-237718015
WHERE version='16' AND type='SQL'
  AND script='V16__unify_stock_history_as_real.sql'
  AND checksum=725522090 AND success=1;
COMMIT;

SELECT version,script,checksum,success FROM flyway_schema_history WHERE version='16';
