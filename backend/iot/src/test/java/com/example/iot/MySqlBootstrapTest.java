package com.example.iot;

import java.net.URI;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses a unique disposable schema; never cleans the database in MYSQL_TEST_URL. */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlBootstrapTest {
    @org.junit.jupiter.api.Test
    void v16MergesImportedAndLiveHistoryWithoutChangingTransactionsOrStock() throws Exception {
        String serverUrl=System.getenv("MYSQL_TEST_URL"),username=System.getenv("MYSQL_TEST_USER"),password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        String schema="unified_history_test_"+UUID.randomUUID().toString().replace("-","");
        URI server=URI.create(serverUrl.substring("jdbc:".length()));
        String url="jdbc:"+new URI(server.getScheme(),server.getAuthority(),"/"+schema,server.getQuery(),null);
        try(var connection=DriverManager.getConnection(serverUrl,username,password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").target("15").load().migrate();
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new DriverManagerDataSource(url,username,password));
                String wh=jdbc.queryForObject("SELECT warehouse_id FROM warehouse WHERE monitoring_slot=1",String.class);
                // An older seed followed by real activity on the same warehouse/product.
                jdbc.update("UPDATE stock_transaction SET created_at=created_at-INTERVAL 3 DAY WHERE seed_batch_id='DEMAND_V13'");
                jdbc.update("UPDATE demand_series SET history_start=history_start-INTERVAL 3 DAY,complete_through=complete_through-INTERVAL 3 DAY");
                var legacyData=new com.example.iot.service.DemandDataService(jdbc);
                jdbc.update("INSERT INTO demand_series VALUES (?,'d13-p03','REAL',DATE(UTC_TIMESTAMP()+INTERVAL 7 HOUR)-INTERVAL 2 DAY,NULL,NULL)",wh);
                String oldDataset=legacyData.snapshot().datasetId();
                jdbc.update("UPDATE warehouse_inventory SET quantity=quantity-3 WHERE warehouse_id=? AND product_id='d13-p03'",wh);
                jdbc.update("UPDATE inventory SET quantity=quantity-3 WHERE product_id='d13-p03'");
                jdbc.update("INSERT INTO stock_transaction (transaction_id,product_id,type,quantity,stock_after,warehouse_id,warehouse_stock_after,created_at,data_source) VALUES ('live-out','d13-p03','OUT',3,997,?,497,UTC_TIMESTAMP()-INTERVAL 1 DAY,'REAL')",wh);
                jdbc.update("INSERT INTO demand_job (job_id,request_key,action,trigger_type,status,attempts,data_cutoff,dataset_id,claim_token,created_at,updated_at) VALUES ('old-job','old-key','TRAIN','INITIAL','RUNNING',1,CURRENT_DATE,'old-dataset','old-claim',UTC_TIMESTAMP(),UTC_TIMESTAMP())");
                jdbc.update("UPDATE demand_cloud_state SET active_model_version='old-model',active_result_job_id='old-job'");
                long count=jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Long.class);
                long stock=jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class);
                long exported=jdbc.queryForObject("SELECT SUM(quantity) FROM stock_transaction WHERE type='OUT'",Long.class);
                var flyway=Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").load();
                assertEquals(1,flyway.migrate().migrationsExecuted);
                flyway.validate();
                assertEquals(0,flyway.migrate().migrationsExecuted);
                assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Long.class));
                assertEquals(stock,jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class));
                assertEquals(exported,jdbc.queryForObject("SELECT SUM(quantity) FROM stock_transaction WHERE type='OUT'",Long.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE data_source<>'REAL'",Integer.class));
                assertEquals(24,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE data_source='REAL' AND complete_through IS NULL",Integer.class));
                assertEquals(24,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series",Integer.class));
                assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM demand_job WHERE job_id='old-job'",String.class));
                org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT claim_token FROM demand_job WHERE job_id='old-job'",String.class));
                org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT active_model_version FROM demand_cloud_state WHERE id=1",String.class));
                var data=new com.example.iot.service.DemandDataService(jdbc);
                var snapshot=data.snapshot();
                assertTrue(!oldDataset.equals(snapshot.datasetId()));
                assertEquals(24*733,snapshot.rows().size());
                assertTrue(snapshot.rows().stream().allMatch(row -> "REAL".equals(row.get("data_source"))));
                String yesterday=java.time.LocalDate.now(com.example.iot.service.DemandDataService.ZONE).minusDays(1).toString();
                var latest=snapshot.rows().stream().filter(row -> wh.equals(row.get("warehouse_id")) && "d13-p03".equals(row.get("product_id")) && yesterday.equals(row.get("date"))).findFirst().orElseThrow();
                assertEquals(3L,latest.get("export_quantity"));
                var warehouseStock=new com.example.iot.service.WarehouseStockService(jdbc);
                assertEquals(498,warehouseStock.change(wh,"d13-p03",1));
                assertEquals("REAL",warehouseStock.source(wh,"d13-p03"));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE warehouse_id=? AND product_id='d13-p03'",Integer.class,wh));
            } finally { statement.execute("DROP DATABASE `"+schema+"`"); }
        }
    }

    @org.junit.jupiter.api.Test
    void v14NormalizesExistingCatalogWithoutChangingStockOrUserEdits() throws Exception {
        String serverUrl=System.getenv("MYSQL_TEST_URL"),username=System.getenv("MYSQL_TEST_USER"),password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        String schema="catalog_label_test_"+UUID.randomUUID().toString().replace("-","");
        URI server=URI.create(serverUrl.substring("jdbc:".length()));
        String url="jdbc:"+new URI(server.getScheme(),server.getAuthority(),"/"+schema,server.getQuery(),null);
        try(var connection=DriverManager.getConnection(serverUrl,username,password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").target("13").load().migrate();
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new DriverManagerDataSource(url,username,password));
                jdbc.update("INSERT INTO product (product_id,sku,name,category,description,unit_weight,unit,deleted) VALUES ('old-sim','SIM_SKU001','Demo product 01','SIMULATION','stable',100,'ITEM',0),('user-p','HH009','User product','Custom','Keep',100,'ITEM',0)");
                jdbc.update("UPDATE product SET name='Trà xanh',category='Đồ uống',description='Thông tin riêng' WHERE product_id='d13-p02'");
                jdbc.update("INSERT INTO monitored_shelf (id,name,warehouse_id,device_code,capacity) SELECT 'old-shelf','SIM_SHELF_001',warehouse_id,'old-device',100 FROM warehouse WHERE monitoring_slot=1");
                long stock=jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class);
                long transactions=jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Long.class);
                Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").target("14").load().migrate();
                assertEquals(13,jdbc.queryForObject("SELECT COUNT(*) FROM product_sku_alias",Integer.class));
                assertEquals("Hàng hóa 001",jdbc.queryForObject("SELECT name FROM product WHERE product_id='old-sim'",String.class));
                assertEquals("Trà xanh",jdbc.queryForObject("SELECT name FROM product WHERE product_id='d13-p02'",String.class));
                assertEquals("Thông tin riêng",jdbc.queryForObject("SELECT description FROM product WHERE product_id='d13-p02'",String.class));
                assertEquals("HH009",jdbc.queryForObject("SELECT sku FROM product WHERE product_id='user-p'",String.class));
                assertEquals("Kệ hàng 001",jdbc.queryForObject("SELECT name FROM monitored_shelf WHERE id='old-shelf'",String.class));
                assertEquals(stock,jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class));
                assertEquals(transactions,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE data_source='SIMULATED'",Long.class));
            } finally { statement.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
    @org.junit.jupiter.api.Test
    void conflictingSeedRollsBackAllV13Data() throws Exception {
        String serverUrl=System.getenv("MYSQL_TEST_URL"),username=System.getenv("MYSQL_TEST_USER"),password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        String schema="seed_failure_test_"+UUID.randomUUID().toString().replace("-","");
        URI server=URI.create(serverUrl.substring("jdbc:".length()));
        String url="jdbc:"+new URI(server.getScheme(),server.getAuthority(),"/"+schema,server.getQuery(),null);
        try(var connection=DriverManager.getConnection(serverUrl,username,password);var statement=connection.createStatement()) {
            statement.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").target("12").load().migrate();
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new DriverManagerDataSource(url,username,password));
                jdbc.update("INSERT INTO product (product_id,sku,name,unit_weight,unit,deleted) VALUES ('conflict','DEMAND_V13_001','Existing',100,'ITEM',0)");
                org.junit.jupiter.api.Assertions.assertThrows(Exception.class,() -> Flyway.configure().dataSource(url,username,password).locations("classpath:db/migration").load().migrate());
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM demand_seed_batch",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Integer.class));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class));
            } finally { statement.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void freshDatabaseMigratesValidatesEntitiesAndRestartsWithoutChanges(boolean upgrade) throws Exception {
        String serverUrl = System.getenv("MYSQL_TEST_URL");
        String username = System.getenv("MYSQL_TEST_USER");
        String password = System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "");
        String schema = "bootstrap_test_" + UUID.randomUUID().toString().replace("-", "");
        URI server = URI.create(serverUrl.substring("jdbc:".length()));
        String databaseUrl = "jdbc:" + new URI(server.getScheme(), server.getAuthority(),
                "/" + schema, server.getQuery(), null);

        try (var connection = DriverManager.getConnection(serverUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                if(upgrade) {
                    Flyway.configure().dataSource(databaseUrl,username,password).locations("classpath:db/migration").target("11").load().migrate();
                    statement.execute("INSERT INTO `"+schema+"`.product (product_id,sku,name,unit_weight,unit,deleted,created_at,updated_at) VALUES ('legacy-p','LEGACY','Legacy',100,'ITEM',0,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
                    statement.execute("INSERT INTO `"+schema+"`.inventory VALUES ('legacy-i','legacy-p',5,UTC_TIMESTAMP())");
                    statement.execute("INSERT INTO `"+schema+"`.stock_transaction (transaction_id,product_id,type,quantity,stock_after,created_at) VALUES ('legacy-t','legacy-p','IN',5,5,UTC_TIMESTAMP())");
                }
                Flyway flyway = Flyway.configure()
                        .dataSource(databaseUrl, username, password)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(false)
                        .load();
                assertTrue(flyway.migrate().migrationsExecuted > 0);
                flyway.validate();

                var entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
                entityManagerFactory.setDataSource(new DriverManagerDataSource(databaseUrl, username, password));
                entityManagerFactory.setPackagesToScan("com.example.iot.entity");
                entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                entityManagerFactory.setJpaPropertyMap(Map.of(
                        "hibernate.hbm2ddl.auto", "validate",
                        "hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
                try {
                    entityManagerFactory.afterPropertiesSet();
                } finally {
                    entityManagerFactory.destroy();
                }

                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM `" + schema
                        + "`.warehouse WHERE monitoring_slot IS NOT NULL")) {
                    assertTrue(rows.next());
                    assertEquals(4, rows.getInt(1));
                }
                assertEquals(0, flyway.migrate().migrationsExecuted);
                var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new DriverManagerDataSource(databaseUrl,username,password));
                assertEquals(30920,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE seed_batch_id='DEMAND_V13'",Integer.class));
                assertEquals(24,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series",Integer.class));
                assertEquals(12,jdbc.queryForObject("SELECT COUNT(*) FROM product_sku_alias",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE name LIKE 'Demo%' OR category='DEMAND_DEMO' OR sku LIKE 'DEMAND_V13_%'",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE note='SIMULATED fixture' OR created_by='DEMAND_V13'",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_inventory wi WHERE wi.quantity<>(SELECT SUM(CASE WHEN t.type='IN' THEN t.quantity ELSE -t.quantity END) FROM stock_transaction t WHERE t.warehouse_id=wi.warehouse_id AND t.product_id=wi.product_id)",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory i WHERE i.product_id LIKE 'd13-p%' AND i.quantity<>(SELECT SUM(wi.quantity) FROM warehouse_inventory wi WHERE wi.product_id=i.product_id)",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM shelf_stock ss JOIN monitored_shelf s ON s.id=ss.shelf_id WHERE ss.quantity>s.capacity",Integer.class));
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE data_source<>'REAL'",Integer.class));
                assertEquals(24,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE data_source='REAL' AND complete_through IS NULL",Integer.class));
                var data=new com.example.iot.service.DemandDataService(jdbc);
                var snapshot=data.snapshot();
                assertEquals("REAL",snapshot.dataSource());
                assertEquals(24,snapshot.pairs().size());assertEquals(24*730,snapshot.rows().size());
                assertEquals(24,data.balances().size());
                assertTrue(data.balances().stream().allMatch(r -> ((Number)r.get("reconciled")).intValue()==1));
                if(upgrade) {
                    assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE transaction_id='legacy-t' AND warehouse_id IS NULL",Integer.class));
                    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_inventory WHERE product_id='legacy-p'",Integer.class));
                }
                String output=System.getenv("DEMAND_SNAPSHOT_OUTPUT");
                if(output!=null&&!upgrade) {
                    var path=java.nio.file.Path.of(output);java.nio.file.Files.createDirectories(path.getParent());
                    java.nio.file.Files.writeString(path,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshot));
                }
                schedulerUsesPersistentPeriodAndDoesNotDuplicate(jdbc,snapshot);
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    void schedulerUsesPersistentPeriodAndDoesNotDuplicate(org.springframework.jdbc.core.JdbcTemplate jdbc,
            com.example.iot.service.DemandDataService.Snapshot snapshot) {
        var worker=org.mockito.Mockito.mock(com.example.iot.service.DemandForecastService.class);
        var data=org.mockito.Mockito.mock(com.example.iot.service.DemandDataService.class);
        org.mockito.Mockito.when(data.snapshot()).thenReturn(snapshot);
        org.mockito.Mockito.when(worker.enabled()).thenReturn(true);
        org.mockito.Mockito.when(worker.health()).thenReturn(Map.of("modelReady",true,"datasetId",snapshot.datasetId()));
        org.mockito.Mockito.when(worker.job(org.mockito.ArgumentMatchers.any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        org.mockito.Mockito.when(worker.submit(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("TRAIN"),org.mockito.ArgumentMatchers.eq(snapshot))).thenReturn(Map.of("status","QUEUED"));
        var jobs=new com.example.iot.service.DemandJobService(jdbc,data,worker);
        jobs.tick();
        org.mockito.Mockito.verify(worker,org.mockito.Mockito.times(1)).submit(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("TRAIN"),org.mockito.ArgumentMatchers.eq(snapshot));
        org.mockito.Mockito.doReturn("{\"status\":\"SUCCEEDED\",\"modelVersion\":\"fixture\"}").when(worker).job(org.mockito.ArgumentMatchers.any());
        jobs.tick();
        org.mockito.Mockito.when(worker.latest()).thenReturn("{\"forecastDate\":\""+java.time.LocalDate.now(com.example.iot.service.DemandDataService.ZONE)+"\"}");
        new com.example.iot.service.DemandJobService(jdbc,data,worker).tick();
        org.mockito.Mockito.verify(worker,org.mockito.Mockito.times(1)).submit(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq("TRAIN"),org.mockito.ArgumentMatchers.eq(snapshot));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_job WHERE status='SUCCEEDED'",Integer.class));
    }
}
