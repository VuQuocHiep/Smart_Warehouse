package com.example.iot.service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;

/** A short database lease elects the dispatcher; Python also deduplicates persistent job IDs. */
@Service
@RequiredArgsConstructor
public class DemandJobService {
    private static final Logger LOG=LoggerFactory.getLogger(DemandJobService.class);
    private final JdbcTemplate jdbc;
    private final DemandDataService data;
    private final DemandForecastService worker;
    private CloudDemandBatchService batch;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void configureBatch(CloudDemandBatchService batch) { this.batch=batch; }
    private boolean batchMode() { return batch!=null && batch.enabled(); }

    public static ZonedDateTime trainingPeriod(ZonedDateTime now) {
        var monday=now.withZoneSameInstant(DemandDataService.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(5).withMinute(0).withSecond(0).withNano(0);
        return now.toInstant().isBefore(monday.toInstant())?monday.minusWeeks(1):monday;
    }
    private String acquire() {
        String owner=UUID.randomUUID().toString();
        return jdbc.update("UPDATE demand_scheduler_lock SET owner=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 120 SECOND WHERE id=1 AND lease_until<UTC_TIMESTAMP(6)",owner)==1?owner:null;
    }
    private void release(String owner) { jdbc.update("UPDATE demand_scheduler_lock SET lease_until=UTC_TIMESTAMP(6) WHERE id=1 AND owner=?",owner); }
    private Map<String,Object> active() {
        return jdbc.queryForList("SELECT * FROM demand_job WHERE status IN ('QUEUED','RUNNING') ORDER BY created_at LIMIT 1").stream().findFirst().orElse(null);
    }
    private Map<String,Object> ensure(String key,String action,String trigger,DemandDataService.Snapshot snapshot) {
        jdbc.update("""
            INSERT INTO demand_job (job_id,request_key,action,trigger_type,status,attempts,data_cutoff,dataset_id,created_at,updated_at)
            VALUES (?,?,?,?,'QUEUED',0,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE request_key=request_key
            """,UUID.randomUUID().toString(),key,action,trigger,snapshot.dataCutoff(),snapshot.datasetId());
        return jdbc.queryForMap("SELECT * FROM demand_job WHERE request_key=?",key);
    }
    private void reconcile(Map<String,Object> job,DemandDataService.Snapshot snapshot) {
        String id=job.get("job_id").toString();
        Map<String,Object> state;
        try { state=DemandForecastService.parse(worker.job(UUID.fromString(id))); }
        catch(ResponseStatusException ex) {
            if(ex.getStatusCode().value()!=404) throw ex;
            // Missing request after a worker restart is safely resubmitted with the same ID.
            if(!snapshot.datasetId().equals(job.get("dataset_id"))) {
                jdbc.update("UPDATE demand_job SET status='FAILED',message='Dataset changed before submission',attempts=3,updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",id);
                return;
            }
            state=worker.submit(id,job.get("action").toString(),snapshot);
            jdbc.update("UPDATE demand_job SET attempts=attempts+1,data_cutoff=? WHERE job_id=?",snapshot.dataCutoff(),id);
        }
        String status=state.get("status").toString();
        if(status.equals("INTERRUPTED")) status="FAILED";
        Object message=state.get("message");
        jdbc.update("UPDATE demand_job SET status=?,model_version=?,message=?,updated_at=UTC_TIMESTAMP(6) WHERE job_id=?",
                status,state.get("modelVersion"),message==null?null:message.toString().substring(0,Math.min(1000,message.toString().length())),id);
    }
    private boolean dispatch(Map<String,Object> job,DemandDataService.Snapshot snapshot) {
        if("SUCCEEDED".equals(job.get("status"))) return false;
        if("FAILED".equals(job.get("status"))) {
            if(((Number)job.get("attempts")).intValue()>=3) return false;
            // Retry once per minute, keeping at most three submitted attempts for a period.
            if(jdbc.queryForObject("SELECT COUNT(*) FROM demand_job WHERE request_key=? AND updated_at<UTC_TIMESTAMP(6)-INTERVAL 60 SECOND",Integer.class,job.get("request_key"))==0) return false;
            String newId=UUID.randomUUID().toString();
            jdbc.update("UPDATE demand_job SET job_id=?,status='QUEUED',updated_at=UTC_TIMESTAMP(6) WHERE request_key=?",newId,job.get("request_key"));
            job=jdbc.queryForMap("SELECT * FROM demand_job WHERE job_id=?",newId);
        }
        reconcile(job,snapshot);
        return true;
    }

    @Scheduled(cron="0 0 5 * * MON",zone="Asia/Ho_Chi_Minh")
    public void weekly() { tick(); }
    @Scheduled(cron="0 10 0 * * *",zone="Asia/Ho_Chi_Minh")
    public void daily() { tick(); }
    @Scheduled(fixedDelay=30000,initialDelay=15000)
    public void tick() {
        if(batchMode()) { batch.tick(); return; }
        if(!worker.enabled()) return;
        String owner=acquire(); if(owner==null) return;
        try {
            var snapshot=data.snapshot();
            var running=active();
            if(running!=null) { reconcile(running,snapshot); return; }
            var health=worker.health();
            boolean usable=Boolean.TRUE.equals(health.get("modelReady")) && snapshot.datasetId().equals(health.get("datasetId"));
            var now=ZonedDateTime.now(DemandDataService.ZONE);
            String key="TRAIN:"+snapshot.datasetId()+":"+trainingPeriod(now).toLocalDate();
            var train=ensure(key,"TRAIN",usable?"WEEKLY_OR_CATCHUP":"INITIAL",snapshot);
            if(!usable && "SUCCEEDED".equals(train.get("status"))) {
                // A lost ML volume requires a new artifact even if this week's DB job succeeded.
                jdbc.update("UPDATE demand_job SET job_id=?,status='QUEUED',attempts=0,updated_at=UTC_TIMESTAMP(6) WHERE request_key=?",UUID.randomUUID().toString(),key);
                train=jdbc.queryForMap("SELECT * FROM demand_job WHERE request_key=?",key);
            }
            if(dispatch(train,snapshot)) return;
            if(!usable || now.toLocalTime().isBefore(LocalTime.of(0,10))) return;
            var latest=DemandForecastService.parse(worker.latest());
            if(!now.toLocalDate().toString().equals(latest.get("forecastDate"))) {
                dispatch(ensure("PREDICT:"+snapshot.datasetId()+":"+now.toLocalDate(),"PREDICT","DAILY",snapshot),snapshot);
            }
        } catch(Exception ex) { LOG.warn("Demand dispatcher will retry: {}",ex.getMessage()); }
        finally { release(owner); }
    }

    public Map<String,Object> startForecast() {
        if(batchMode()) return batch.startForecast();
        if(!worker.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Dịch vụ dự báo chưa bật.");
        String owner=acquire();
        if(owner==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bộ điều phối đang cập nhật. Hãy thử lại.");
        try {
            var running=active(); if(running!=null) return response(running);
            var snapshot=data.snapshot();
            var health=worker.health();
            if(!Boolean.TRUE.equals(health.get("modelReady")) || !snapshot.datasetId().equals(health.get("datasetId")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa có model phù hợp. Job khởi tạo sẽ tự train sau khi ML sẵn sàng.");
            var job=ensure("MANUAL:"+UUID.randomUUID(),"PREDICT","MANUAL",snapshot);
            reconcile(job,snapshot);
            return response(jdbc.queryForMap("SELECT * FROM demand_job WHERE job_id=?",job.get("job_id")));
        } finally { release(owner); }
    }
    private Map<String,Object> response(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        result.put("jobId",row.get("job_id"));result.put("status",row.get("status"));result.put("action",row.get("action"));result.put("message",row.get("message"));
        return result;
    }
    public Map<String,Object> status() {
        var result=new LinkedHashMap<String,Object>();
        var jobs=jdbc.queryForList("SELECT job_id,action,trigger_type,status,attempts,message,model_version,updated_at FROM demand_job ORDER BY created_at DESC LIMIT 10");
        result.put("jobs",jobs);
        result.put("nextTrainingAt",trainingPeriod(ZonedDateTime.now(DemandDataService.ZONE)).plusWeeks(1).toOffsetDateTime().toString());
        result.put("executionMode",batchMode()?"GITHUB_BATCH":"HTTP_WORKER");
        return result;
    }
}
