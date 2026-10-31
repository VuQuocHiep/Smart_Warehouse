package com.example.iot;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces Aiven's required-primary-key policy and both previously deployed V16 states. */
@EnabledIfEnvironmentVariable(named="MYSQL_TEST_URL",matches="jdbc:mysql:.*")
class V16PrimaryKeyMigrationTest {
    String serverUrl,user,password,schema,url;
    DriverManagerDataSource source;
    JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        serverUrl=System.getenv("MYSQL_TEST_URL");
        user=System.getenv("MYSQL_TEST_USER");
        password=System.getenv().getOrDefault("MYSQL_TEST_PASSWORD","");
        schema="v16_primary_key_test_"+UUID.randomUUID().toString().replace("-","");
        var server=URI.create(serverUrl.substring(5));
        url="jdbc:"+new URI(server.getScheme(),server.getAuthority(),"/"+schema,server.getQuery(),null);
        try(var c=DriverManager.getConnection(serverUrl,user,password);var s=c.createStatement()) {
            s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
        }
        source=new DriverManagerDataSource(url,user,password);
        jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .initSql("SET SESSION sql_require_primary_key=ON").target("15").load().migrate();
    }

    @AfterEach void cleanup() throws Exception {
        try(var c=DriverManager.getConnection(serverUrl,user,password);var s=c.createStatement()) {
            s.execute("DROP DATABASE `"+schema+"`");
        }
    }

    void repairKnownV16() throws Exception {
        try(var c=source.getConnection()) {
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/maintenance/repair_v16_primary_key.sql"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void recoversFailedAivenAndSuccessfulLocalV16WithoutReplayingSuccessfulData(boolean previouslySucceeded) throws Exception {
        var oldResource=new ClassPathResource("db/legacy-v16/V16__unify_stock_history_as_real.sql");
        var fixedResource=new ClassPathResource("db/migration/V16__unify_stock_history_as_real.sql");
        // The temporary table is the only changed SQL; successful ledgers need no replay.
        assertEquals(oldResource.getContentAsString(StandardCharsets.UTF_8).split("START TRANSACTION;",2)[1],
                fixedResource.getContentAsString(StandardCharsets.UTF_8).split("START TRANSACTION;",2)[1]);
        var old=Flyway.configure().dataSource(source).locations("classpath:db/legacy-v16")
                .validateOnMigrate(false)
                .initSql("SET SESSION sql_require_primary_key="+(previouslySucceeded?"OFF":"ON")).load();
        if(previouslySucceeded) {
            assertEquals(1,old.migrate().migrationsExecuted);
        } else {
            var error=assertThrows(Exception.class,old::migrate);
            Throwable cause=error;
            while(cause.getCause()!=null) cause=cause.getCause();
            assertInstanceOf(SQLException.class,cause);
            assertEquals(3750,((SQLException)cause).getErrorCode());
            assertEquals(30920,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE data_source='SIMULATED'",Integer.class));
        }
        assertEquals(725522090,jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='16'",Integer.class));
        var otherHistory=jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'16' OR version IS NULL ORDER BY installed_rank");
        long stock=jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class);
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Long.class);
        var fixed=Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .initSql("SET SESSION sql_require_primary_key=ON").load();
        assertFalse(fixed.validateWithResult().validationSuccessful);

        // An unrelated checksum must not be silently accepted by the recovery script.
        jdbc.update("UPDATE flyway_schema_history SET checksum=123 WHERE version='16'");
        repairKnownV16();
        assertEquals(123,jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='16'",Integer.class));
        jdbc.update("UPDATE flyway_schema_history SET checksum=725522090 WHERE version='16'");
        repairKnownV16();
        if(previouslySucceeded) fixed.validate();
        assertEquals(previouslySucceeded?0:1,fixed.migrate().migrationsExecuted);
        fixed.validate();
        assertEquals(-237718015,jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='16' AND success=1",Integer.class));
        assertEquals(stock,jdbc.queryForObject("SELECT SUM(quantity) FROM warehouse_inventory",Long.class));
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM stock_transaction WHERE data_source<>'REAL'",Integer.class));
        assertEquals(24,jdbc.queryForObject("SELECT COUNT(*) FROM demand_series WHERE data_source='REAL' AND complete_through IS NULL",Integer.class));
        assertEquals(otherHistory,jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'16' OR version IS NULL ORDER BY installed_rank"));
        repairKnownV16();
        fixed.validate();
        assertEquals(0,fixed.migrate().migrationsExecuted);
    }
}
