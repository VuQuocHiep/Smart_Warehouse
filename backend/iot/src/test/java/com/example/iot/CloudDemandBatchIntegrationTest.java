package com.example.iot;

import com.example.iot.service.*;
import java.net.URI;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a fresh schema and real MySQL locking/rollback, never the user's application schema. */
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches="jdbc:mysql:.*")
class CloudDemandBatchIntegrationTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DemandDataService data;
    CloudDemandBatchService batch;
    GitHubDemandDispatcher github;
    String schema,serverUrl,user,password;
    DemandDataService.Snapshot snapshot;
    static final byte[] MODEL="test-joblib".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeEach void setup() throws Exception {
        serverUrl=System.getenv("MYSQL_TEST_URL");user=System.getenv("MYSQL_TEST_USER");password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        schema="cloud_batch_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(serverUrl,user,password);var s=c.createStatement()) { s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4"); }
        var server=URI.create(serverUrl.substring(5));
        String url="jdbc:"+new URI(server.getScheme(),server.getAuthority(),"/"+schema,server.getQuery(),null);
        // V15 depends only on demand_job; the complete migration chain is tested by MySqlBootstrapTest.
        var source=new DriverManagerDataSource(url,user,password);jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE demand_job (job_id VARCHAR(36) PRIMARY KEY,request_key VARCHAR(255) UNIQUE,action VARCHAR(16),trigger_type VARCHAR(24),status VARCHAR(24),attempts INT DEFAULT 0,data_cutoff DATE,dataset_id VARCHAR(128),model_version VARCHAR(128),message VARCHAR(1000),created_at DATETIME(6),updated_at DATETIME(6)) ENGINE=InnoDB");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("14").load().migrate();
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        data=mock(DemandDataService.class);github=mock(GitHubDemandDispatcher.class);
        String cutoff=LocalDate.now(DemandDataService.ZONE).minusDays(1).toString();
        snapshot=new DemandDataService.Snapshot(List.of(new DemandDataService.Pair("w","Kho 1","p","SKU","Product")),List.of(),cutoff,"SIMULATED","fixture");
        when(data.snapshot()).thenReturn(snapshot);
        batch=newBatch();
    }
    CloudDemandBatchService newBatch() { return new CloudDemandBatchService(jdbc,data,github,"GITHUB_BATCH","x".repeat(32)); }
    @AfterEach void cleanup() throws Exception {
        try(var c=DriverManager.getConnection(serverUrl,user,password);var s=c.createStatement()) { s.execute("DROP DATABASE `"+schema+"`"); }
    }
    Map<String,Object> claim(String runner) { return tx.execute(status->batch.claim(runner)); }
    @SuppressWarnings("unchecked")
    Map<String,Object> result(Map<String,Object> claim,String version) {
        LocalDate start=LocalDate.parse(snapshot.dataCutoff()).plusDays(1);
        var rows=new ArrayList<Map<String,Object>>();
        for(int i=0;i<14;i++) rows.add(Map.of("warehouse_id","w","product_id","p","target_date",start.plusDays(i).toString(),"status","OK","predicted_quantity",10,"model_version",version,"data_source","SIMULATED","data_cutoff",snapshot.dataCutoff()));
        return Map.of("runId",claim.get("jobId"),"modelVersion",version,"datasetId","fixture","dataSource","SIMULATED","dataCutoff",snapshot.dataCutoff(),"forecastDate",start.toString(),"forecasts",rows,"pairs",List.of(Map.of("warehouseId","w","productId","p")));
    }
    Map<String,Object> metadata(String version) {
        return Map.of("modelVersion",version,"datasetId","fixture","dataSource","SIMULATED","dataCutoff",snapshot.dataCutoff(),"formatVersion",1,"lightgbmVersion","test","sklearnVersion","test","artifactSha256",CloudDemandBatchService.sha256(MODEL));
    }
    Map<String,Object> complete(Map<String,Object> claim,String version) {
        return tx.execute(status->batch.complete(claim.get("jobId").toString(),claim.get("claimToken").toString(),result(claim,version),metadata(version),"TRAIN".equals(claim.get("action"))?MODEL:null));
    }
    void fail(Map<String,Object> claim) { tx.execute(status->batch.fail(claim.get("jobId").toString(),claim.get("claimToken").toString())); }

    @Test void restartReusesModelAndFailedPredictionDoesNotReplacePublishedResult() {
        var train=claim("runner");assertEquals("TRAIN",train.get("action"));complete(train,"version-1");
        String published=batch.latest();batch=newBatch();assertEquals(published,batch.latest());
        jdbc.update("UPDATE demand_cloud_state SET last_dispatch_at=UTC_TIMESTAMP(6)");
        tx.execute(status->batch.startForecast());
        verify(github,times(1)).dispatch(); // Manual action must not wait for the automatic dispatch cooldown.
        var predict=claim("runner");assertEquals("PREDICT",predict.get("action"));
        assertArrayEquals(MODEL,tx.execute(status->batch.artifact(predict.get("jobId").toString(),predict.get("claimToken").toString())));
        fail(predict);assertEquals(published,batch.latest());
        assertNull(jdbc.queryForObject("SELECT snapshot_json FROM demand_job WHERE job_id=?",String.class,predict.get("jobId")));
        var retry=claim("retry");assertEquals(predict.get("jobId"),retry.get("jobId"));
        assertNotEquals(predict.get("claimToken"),retry.get("claimToken"));
        assertThrows(ResponseStatusException.class,()->complete(predict,"version-1"));
        complete(retry,"version-1");assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_model_artifact",Integer.class));
    }
    @Test void idempotentClaimAndCompletionAndOnlyOneConcurrentOwner() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{ gate.await();return claim("runner-a"); });
            var second=pool.submit(()->{ gate.await();return claim("runner-b"); });gate.countDown();
            var a=first.get(15,TimeUnit.SECONDS);var b=second.get(15,TimeUnit.SECONDS);
            assertEquals(1,List.of(a,b).stream().filter(v->v.containsKey("jobId")).count());
            var owner=a.containsKey("jobId")?a:b;
            var again=claim(a.containsKey("jobId")?"runner-a":"runner-b");
            assertEquals(owner.get("claimToken"),again.get("claimToken"));
            assertEquals(1,jdbc.queryForObject("SELECT attempts FROM demand_job",Integer.class));
            complete(owner,"version-1");complete(owner,"version-1");
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_forecast_result",Integer.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void invalidChecksumRollsBackAndThreeFailuresStopRetrying() {
        var train=claim("runner");
        assertThrows(ResponseStatusException.class,()->tx.execute(status->batch.complete(train.get("jobId").toString(),train.get("claimToken").toString(),result(train,"v1"),metadata("v1"),new byte[]{1})));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM demand_model_artifact",Integer.class));
        fail(train);fail(claim("retry-2"));fail(claim("retry-3"));
        assertEquals("IDLE",claim("retry-4").get("status"));
        assertEquals(3,jdbc.queryForObject("SELECT attempts FROM demand_job",Integer.class));
    }
    @Test void expiredOwnerCannotPublishAndModelRetentionKeepsTwoVersions() {
        for(int i=1;i<=3;i++) {
            var train=claim("runner-"+i);
            if(i==1) {
                jdbc.update("UPDATE demand_job SET claim_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
                var expired=train;
                assertThrows(ResponseStatusException.class,()->complete(expired,"v1"));train=claim("replacement");
            }
            complete(train,"v"+i);
            if(i<3) jdbc.update("UPDATE demand_job SET request_key=CONCAT(request_key,?) WHERE action='TRAIN'","-old-"+i);
            if(i==1) jdbc.update("UPDATE demand_forecast_result SET forecast_date=DATE(UTC_TIMESTAMP())-INTERVAL 20 DAY");
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM demand_model_artifact",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM demand_forecast_result",Integer.class));
        assertEquals("v3",jdbc.queryForObject("SELECT active_model_version FROM demand_cloud_state",String.class));
    }
    @Test void invalidRunnerTokenCannotUseBatchEvenWithOrdinaryUserAccess() {
        assertThrows(ResponseStatusException.class,()->batch.authorize(""));
        assertThrows(ResponseStatusException.class,()->batch.authorize("user-jwt"));
        assertDoesNotThrow(()->batch.authorize("x".repeat(32)));
    }
}
