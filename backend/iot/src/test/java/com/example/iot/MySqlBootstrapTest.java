package com.example.iot;

import java.net.URI;
import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses a unique disposable schema; never cleans the database in MYSQL_TEST_URL. */
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
class MySqlBootstrapTest {
    @Test
    void freshDatabaseMigratesValidatesEntitiesAndRestartsWithoutChanges() throws Exception {
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
            } finally {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }
}
