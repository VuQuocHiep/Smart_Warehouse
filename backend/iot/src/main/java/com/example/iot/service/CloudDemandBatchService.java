package com.example.iot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** The database owns job leases and active pointers; runner files are disposable. */
@Service
public class CloudDemandBatchService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private final JdbcTemplate jdbc;
    private final DemandDataService data;
    private final GitHubDemandDispatcher github;
    private final boolean enabled;
    private final String token;
    private final AtomicLong lastTick=new AtomicLong();

    public CloudDemandBatchService(JdbcTemplate jdbc,DemandDataService data,GitHubDemandDispatcher github,
            @Value("${demand.execution-mode:HTTP_WORKER}") String mode,
            @Value("${demand.batch.token:}") String token) {
        if (!Set.of("HTTP_WORKER","GITHUB_BATCH").contains(mode)) throw new IllegalArgumentException("Invalid DEMAND_EXECUTION_MODE");
        this.jdbc=jdbc; this.data=data; this.github=github; this.enabled="GITHUB_BATCH".equals(mode); this.token=token;
        if (enabled && token.length()<32) throw new IllegalArgumentException("DEMAND_BATCH_TOKEN must contain at least 32 characters");
    }
    public boolean enabled() { return enabled; }
    public void authorize(String supplied) {
        if (!enabled) throw error(HttpStatus.NOT_FOUND,"Batch mode is disabled");
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw error(HttpStatus.UNAUTHORIZED,"Invalid batch token");
    }
    public static String json(Object value) { return JSON.writeValueAsString(value); }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
    private void lock() { jdbc.queryForObject("SELECT id FROM demand_cloud_state WHERE id=1 FOR UPDATE",Integer.class); }
    private Map<String,Object> first(String sql,Object... args) { return jdbc.queryForList(sql,args).stream().findFirst().orElse(null); }
    private Map<String,Object> model() {
        return first("SELECT m.model_version,m.dataset_id,m.data_source,m.sha256,m.metadata_json FROM demand_model_artifact m JOIN demand_cloud_state s ON s.active_model_version=m.model_version WHERE s.id=1");
    }
    private void expire() {
        jdbc.update("UPDATE demand_job SET status='FAILED',message='Runner lease expired; retry is allowed',claim_token=NULL,snapshot_json=NULL,updated_at=UTC_TIMESTAMP(6) WHERE status='RUNNING' AND claim_until<UTC_TIMESTAMP(6)");
    }
    private Map<String,Object> ensure(String key,String action,String trigger,DemandDataService.Snapshot snapshot) {
        jdbc.update("""
            INSERT INTO demand_job (job_id,request_key,action,trigger_type,status,attempts,data_cutoff,dataset_id,created_at,updated_at)
            VALUES (?,?,?,?,'QUEUED',0,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE request_key=request_key
            """,UUID.randomUUID().toString(),key,action,trigger,snapshot.dataCutoff(),snapshot.datasetId());
        return jdbc.queryForMap("SELECT * FROM demand_job WHERE request_key=?",key);
    }
    private Map<String,Object> prepare(DemandDataService.Snapshot snapshot,boolean manual) {
        expire();
        var running=first("SELECT * FROM demand_job WHERE status='RUNNING' ORDER BY created_at LIMIT 1");
        if (running!=null) return running;
        // Stale queued snapshots must never block a replacement dataset.
        jdbc.update("UPDATE demand_job SET status='FAILED',attempts=3,snapshot_json=NULL,message='Dataset changed before execution',updated_at=UTC_TIMESTAMP(6) WHERE status IN ('QUEUED','FAILED') AND dataset_id<>?",snapshot.datasetId());
        var current=model();
        boolean usable=current!=null && snapshot.datasetId().equals(current.get("dataset_id")) && snapshot.dataSource().equals(current.get("data_source"));
        var now=ZonedDateTime.now(DemandDataService.ZONE);
        var train=ensure("TRAIN:"+snapshot.datasetId()+":"+DemandJobService.trainingPeriod(now).toLocalDate(),"TRAIN",usable?"WEEKLY_OR_CATCHUP":"INITIAL",snapshot);
        if (!"SUCCEEDED".equals(train.get("status"))) {
            if (((Number)train.get("attempts")).intValue()<3) return train;
            // An exhausted weekly train must not prevent prediction with a compatible previous model.
            if (!usable) return null;
        }
        if (!usable) return null;
        var queued=first("SELECT * FROM demand_job WHERE status IN ('QUEUED','FAILED') AND attempts<3 AND action='PREDICT' AND dataset_id=? ORDER BY created_at LIMIT 1",snapshot.datasetId());
        if (queued!=null) return queued;
        if (manual) return ensure("MANUAL:"+UUID.randomUUID(),"PREDICT","MANUAL",snapshot);
        if (now.toLocalTime().isBefore(LocalTime.of(0,10))) return null;
        var result=first("SELECT r.forecast_date FROM demand_forecast_result r JOIN demand_cloud_state s ON s.active_result_job_id=r.job_id WHERE s.id=1");
        if (result!=null && now.toLocalDate().toString().equals(result.get("forecast_date").toString())) return null;
        var predict=ensure("PREDICT:"+snapshot.datasetId()+":"+now.toLocalDate(),"PREDICT","DAILY",snapshot);
        return "SUCCEEDED".equals(predict.get("status")) || ((Number)predict.get("attempts")).intValue()>=3 ? null : predict;
    }
    private void dispatch(Map<String,Object> job) {
        if (job==null || "RUNNING".equals(job.get("status"))) return;
        boolean manual="MANUAL".equals(job.get("trigger_type"));
        int acquired=jdbc.update("UPDATE demand_cloud_state SET last_dispatch_at=UTC_TIMESTAMP(6) WHERE id=1 AND (? OR last_dispatch_at IS NULL OR last_dispatch_at<UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE)",manual);
        if (acquired==0) return;
        try { github.dispatch(); }
        catch(Exception ex) {
            // Do not persist HTTP errors: they may contain credentials or infrastructure details.
            jdbc.update("UPDATE demand_job SET message='GitHub dispatch unavailable; check repository, workflow and token, or run workflow manually',updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",job.get("job_id"));
        }
    }
    @Transactional
    public void tick() {
        if (!enabled) return;
        long now=System.currentTimeMillis(),previous=lastTick.get();
        if (now-previous<300000 || !lastTick.compareAndSet(previous,now)) return;
        lock(); dispatch(prepare(data.snapshot(),false));
    }
    @Transactional
    public Map<String,Object> startForecast() {
        lock();
        var job=prepare(data.snapshot(),true);
        if (job==null) throw error(HttpStatus.CONFLICT,"Không có model phù hợp; kiểm tra job train thất bại.");
        dispatch(job);
        return jobView(jdbc.queryForMap("SELECT * FROM demand_job WHERE job_id=?",job.get("job_id")));
    }
    @Transactional
    public Map<String,Object> claim(String runner) {
        if (runner==null || !runner.matches("[A-Za-z0-9_.-]{1,128}")) throw error(HttpStatus.BAD_REQUEST,"Invalid runner ID");
        lock();
        var snapshot=data.snapshot();
        var job=prepare(snapshot,false);
        if (job==null) return Map.of("status","IDLE");
        if ("RUNNING".equals(job.get("status"))) {
            if (!runner.equals(job.get("runner_id"))) return Map.of("status","IDLE");
            return claimView(job,DemandForecastService.parse(job.get("snapshot_json").toString()),job.get("claim_token").toString());
        }
        if (((Number)job.get("attempts")).intValue()>=3) return Map.of("status","IDLE");
        String lease=UUID.randomUUID().toString();
        String id=job.get("job_id").toString();
        jdbc.update("UPDATE demand_job SET status='RUNNING',attempts=attempts+1,claim_token=?,claim_until=UTC_TIMESTAMP(6)+INTERVAL 50 MINUTE,runner_id=?,snapshot_json=?,data_cutoff=?,message=NULL,updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",
                lease,runner,json(snapshot),snapshot.dataCutoff(),id);
        return claimView(job,snapshot,lease);
    }
    private Map<String,Object> claimView(Map<String,Object> job,Object snapshot,String lease) {
        var result=new LinkedHashMap<String,Object>();
        result.put("jobId",job.get("job_id")); result.put("action",job.get("action")); result.put("claimToken",lease); result.put("snapshot",snapshot);
        if ("PREDICT".equals(job.get("action"))) {
            var current=model();
            result.put("modelVersion",current.get("model_version")); result.put("modelSha256",current.get("sha256"));
            result.put("modelMetadata",DemandForecastService.parse(current.get("metadata_json").toString()));
        }
        return result;
    }
    private Map<String,Object> attempt(String id,String lease,boolean allowComplete) {
        var job=first("SELECT * FROM demand_job WHERE job_id=? FOR UPDATE",id);
        if (job==null) throw error(HttpStatus.NOT_FOUND,"Unknown job");
        if (lease.isBlank() || !lease.equals(job.get("claim_token"))) throw error(HttpStatus.CONFLICT,"This attempt no longer owns the job");
        if (allowComplete && "SUCCEEDED".equals(job.get("status"))) return job;
        if (!"RUNNING".equals(job.get("status")) || jdbc.queryForObject("SELECT COUNT(*) FROM demand_job WHERE job_id=? AND claim_until>UTC_TIMESTAMP(6)",Integer.class,id)==0)
            throw error(HttpStatus.CONFLICT,"Job lease expired or job is no longer running");
        return job;
    }
    @Transactional
    public byte[] artifact(String id,String lease) {
        lock(); var job=attempt(id,lease,false);
        if (!"PREDICT".equals(job.get("action"))) throw error(HttpStatus.CONFLICT,"Only a prediction job can download its model");
        return jdbc.queryForObject("SELECT m.artifact FROM demand_model_artifact m JOIN demand_cloud_state s ON s.active_model_version=m.model_version WHERE s.id=1",byte[].class);
    }
    @Transactional
    public Map<String,Object> complete(String id,String lease,Map<String,Object> result,Map<String,Object> metadata,byte[] artifact) {
        lock(); var job=attempt(id,lease,true);
        if ("SUCCEEDED".equals(job.get("status"))) return jobView(job);
        validate(job,result,metadata);
        String version=result.get("modelVersion").toString();
        if ("TRAIN".equals(job.get("action"))) {
            if (artifact==null || artifact.length==0 || artifact.length>8*1024*1024) throw error(HttpStatus.BAD_REQUEST,"Model artifact must be between 1 byte and 8 MiB");
            String sha=sha256(artifact);
            if (!sha.equals(metadata.get("artifactSha256"))) throw error(HttpStatus.BAD_REQUEST,"Artifact checksum mismatch");
            var existing=first("SELECT dataset_id FROM demand_model_artifact WHERE model_version=?",version);
            if (existing!=null && !job.get("dataset_id").equals(existing.get("dataset_id")))
                throw error(HttpStatus.CONFLICT,"Model version belongs to a different dataset");
            // Retraining an unchanged bounded fixture can legitimately produce the same model version.
            jdbc.update("""
                INSERT INTO demand_model_artifact VALUES (?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE training_job_id=VALUES(training_job_id),sha256=VALUES(sha256),
                  metadata_json=VALUES(metadata_json),artifact=VALUES(artifact),created_at=VALUES(created_at)
                """,version,id,job.get("dataset_id"),result.get("dataSource"),sha,json(metadata),artifact);
        } else {
            var current=model();
            if (current==null || !version.equals(current.get("model_version"))) throw error(HttpStatus.CONFLICT,"Active model changed");
        }
        jdbc.update("INSERT INTO demand_forecast_result VALUES (?,?,?,?,UTC_TIMESTAMP(6))",id,version,result.get("forecastDate"),json(result));
        jdbc.update("UPDATE demand_cloud_state SET active_model_version=?,active_result_job_id=? WHERE id=1",version,id);
        jdbc.update("UPDATE demand_job SET status='SUCCEEDED',model_version=?,message=NULL,snapshot_json=NULL,updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",version,id);
        prune();
        return jobView(jdbc.queryForMap("SELECT * FROM demand_job WHERE job_id=?",id));
    }
    public static String sha256(byte[] artifact) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact)); }
        catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @SuppressWarnings("unchecked")
    private void validate(Map<String,Object> job,Map<String,Object> result,Map<String,Object> metadata) {
        var snapshot=DemandForecastService.parse(job.get("snapshot_json").toString());
        String cutoff=job.get("data_cutoff").toString();
        if (!job.get("job_id").equals(result.get("runId")) || !job.get("dataset_id").equals(result.get("datasetId"))
                || !snapshot.get("dataSource").equals(result.get("dataSource")) || !cutoff.equals(result.get("dataCutoff"))
                || !LocalDate.parse(cutoff).plusDays(1).toString().equals(result.get("forecastDate"))
                || !(result.get("modelVersion") instanceof String version) || !version.matches("[A-Za-z0-9_.-]{1,128}")
                || !version.equals(metadata.get("modelVersion")) || !job.get("dataset_id").equals(metadata.get("datasetId"))
                || !snapshot.get("dataSource").equals(metadata.get("dataSource"))
                || !cutoff.equals(metadata.get("dataCutoff")) || !(metadata.get("formatVersion") instanceof Number)
                || !(metadata.get("lightgbmVersion") instanceof String) || !(metadata.get("sklearnVersion") instanceof String))
            throw error(HttpStatus.BAD_REQUEST,"Result does not match the claimed snapshot or model metadata");
        if (!(result.get("forecasts") instanceof List<?> rows) || !(result.get("pairs") instanceof List<?>))
            throw error(HttpStatus.BAD_REQUEST,"Missing forecasts or catalog");
        var pairs=(List<Map<String,Object>>)snapshot.get("pairs");
        var keys=new HashSet<String>();
        for (var p:pairs) keys.add(p.get("warehouseId")+":"+p.get("productId"));
        var seen=new HashSet<String>();
        for (Object value:rows) {
            if (!(value instanceof Map<?,?> row)) throw error(HttpStatus.BAD_REQUEST,"Invalid forecast row");
            String key=row.get("warehouse_id")+":"+row.get("product_id");
            LocalDate target;
            try { target=LocalDate.parse(String.valueOf(row.get("target_date"))); }
            catch(Exception ex) { throw error(HttpStatus.BAD_REQUEST,"Invalid forecast date"); }
            long day=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(cutoff),target);
            Object quantity=row.get("predicted_quantity");
            if (!keys.contains(key) || day<1 || day>14 || !seen.add(key+":"+target)
                    || !version.equals(row.get("model_version")) || !snapshot.get("dataSource").equals(row.get("data_source"))
                    || !cutoff.equals(row.get("data_cutoff"))
                    || ("OK".equals(row.get("status")) && (!(quantity instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<0)))
                throw error(HttpStatus.BAD_REQUEST,"Invalid forecast series or quantity");
        }
        if (seen.size()!=keys.size()*14) throw error(HttpStatus.BAD_REQUEST,"Expected 14 days per catalog pair");
    }
    private void prune() {
        jdbc.update("DELETE FROM demand_forecast_result WHERE forecast_date<DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)-INTERVAL 13 DAY AND job_id<>(SELECT active_result_job_id FROM demand_cloud_state WHERE id=1)");
        var keep=jdbc.queryForList("SELECT model_version FROM demand_model_artifact ORDER BY created_at DESC,model_version DESC LIMIT 2",String.class);
        if (keep.size()==2) jdbc.update("DELETE FROM demand_model_artifact WHERE model_version NOT IN (?,?) AND model_version<>(SELECT active_model_version FROM demand_cloud_state WHERE id=1)",keep.get(0),keep.get(1));
        jdbc.update("UPDATE demand_job SET snapshot_json=NULL WHERE status='FAILED' AND attempts>=3");
    }
    @Transactional
    public Map<String,Object> fail(String id,String lease) {
        lock(); var job=attempt(id,lease,false);
        jdbc.update("UPDATE demand_job SET status='FAILED',message='Runner failed; see GitHub Actions logs',claim_token=NULL,snapshot_json=NULL,updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",id);
        return Map.of("jobId",id,"status","FAILED");
    }
    @Transactional(readOnly=true)
    public String latest() {
        var row=first("SELECT r.result_json FROM demand_forecast_result r JOIN demand_cloud_state s ON s.active_result_job_id=r.job_id WHERE s.id=1");
        if (row==null) return json(Map.of("forecasts",List.of(),"history",List.of(),"pairs",List.of()));
        var result=DemandForecastService.parse(row.get("result_json").toString());
        result.put("nextTrainingAt",DemandJobService.trainingPeriod(ZonedDateTime.now(DemandDataService.ZONE)).plusWeeks(1).toOffsetDateTime().toString());
        return json(result);
    }
    public String job(UUID id) {
        var row=first("SELECT * FROM demand_job WHERE job_id=?",id.toString());
        if (row==null) throw error(HttpStatus.NOT_FOUND,"Unknown job");
        return json(jobView(row));
    }
    private Map<String,Object> jobView(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        result.put("jobId",row.get("job_id")); result.put("status",row.get("status")); result.put("action",row.get("action"));
        result.put("modelVersion",row.get("model_version")); result.put("message",row.get("message"));
        return result;
    }
}
