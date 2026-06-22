/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.support;

import com.redis.testcontainers.RedisContainer;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for every {@code @SpringBootTest} integration test.
 *
 * <p>Provides an isolated, freshly-migrated database via TWO interchangeable paths,
 * selected by the {@code test.db} system property:
 *
 * <ul>
 *   <li><b>local</b> (default) — a dedicated {@code gemek_test} database on the running
 *       dev Postgres (localhost:5433). It is created if missing, then Flyway
 *       <em>clean + migrate</em> runs once per JVM so every {@code mvnw test} starts from
 *       an EMPTY schema (V1..V17) and {@code AdminSeeder} seeds the test admin
 *       (phone {@code 0900000000} / {@code GemekAdmin2026}) on an empty DB. No manual step.</li>
 *   <li><b>testcontainers</b> ({@code -Dtest.db=testcontainers}) — singleton PostgreSQL +
 *       Redis containers (CI path). Used where Testcontainers can reach Docker; it does NOT
 *       run by default locally, where Docker Desktop 29.5.2 / API 1.54 rejects docker-java 3.4.1.</li>
 * </ul>
 *
 * <p><b>Safety:</b> Flyway {@code clean()} is destructive. It runs ONLY in the local path and
 * ONLY against a hardcoded URL ending in {@code /gemek_test}; an explicit guard throws if the
 * target is anything else, so the dev {@code gemek} database can never be cleaned.
 */
public abstract class AbstractIntegrationTest {

    /** Path switch: {@code -Dtest.db=testcontainers} activates the CI container path; default is local. */
    private static final boolean USE_TESTCONTAINERS =
            "testcontainers".equalsIgnoreCase(System.getProperty("test.db", "local"));

    // ---- Local dedicated test DB (default path) ----
    private static final String LOCAL_BASE_URL  = "jdbc:postgresql://localhost:5433/";
    private static final String LOCAL_TEST_DB   = "gemek_test";
    private static final String LOCAL_TEST_URL  = LOCAL_BASE_URL + LOCAL_TEST_DB;
    private static final String LOCAL_MAINT_URL = LOCAL_BASE_URL + "gemek";
    private static final String LOCAL_USER      = "gemek";
    private static final String LOCAL_PASS      = "GemekLocal@2026";

    // ---- CI containers (testcontainers path) ----
    static final PostgreSQLContainer<?> POSTGRES;
    static final RedisContainer REDIS;

    static {
        if (USE_TESTCONTAINERS) {
            POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.18-alpine"))
                    .withDatabaseName("gemek")
                    .withUsername("gemek")
                    .withPassword("test");
            REDIS = new RedisContainer(DockerImageName.parse("redis:7.2.4-alpine"));
            POSTGRES.start();
            REDIS.start();
        } else {
            POSTGRES = null;
            REDIS = null;
            prepareLocalTestDatabase();
        }
    }

    /**
     * Ensures {@code gemek_test} exists, then Flyway clean+migrate so every run starts empty.
     *
     * @throws IllegalStateException if the DB cannot be prepared, or the clean target is not gemek_test.
     */
    private static void prepareLocalTestDatabase() {
        // 1. Create gemek_test if absent — connect to the dev 'gemek' DB only as a maintenance session.
        try (Connection conn = DriverManager.getConnection(LOCAL_MAINT_URL, LOCAL_USER, LOCAL_PASS);
             Statement st = conn.createStatement()) {
            boolean exists;
            try (ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + LOCAL_TEST_DB + "'")) {
                exists = rs.next();
            }
            // CREATE DATABASE cannot run inside a transaction or with IF NOT EXISTS — guard manually.
            if (!exists) {
                st.executeUpdate("CREATE DATABASE " + LOCAL_TEST_DB);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create/verify the " + LOCAL_TEST_DB + " test database", e);
        }

        // 2. SAFETY GUARD — never clean anything that is not gemek_test.
        if (!LOCAL_TEST_URL.endsWith("/" + LOCAL_TEST_DB)) {
            throw new IllegalStateException("Refusing Flyway clean: target is not gemek_test -> " + LOCAL_TEST_URL);
        }

        // 3. Clean (drop all objects) + migrate (V1..V17) so the schema is rebuilt from scratch each run.
        Flyway flyway = Flyway.configure()
                .dataSource(LOCAL_TEST_URL, LOCAL_USER, LOCAL_PASS)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    /**
     * Rebinds datasource/Redis to the containers in the testcontainers path.
     *
     * <p>In the local path the datasource comes from {@code application-test.yml} (pointed at
     * {@code gemek_test}) and Redis from the running dev instance, so nothing is registered here.
     *
     * @param registry the Spring dynamic property registry.
     */
    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        if (USE_TESTCONTAINERS) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.data.redis.host", REDIS::getRedisHost);
            registry.add("spring.data.redis.port", REDIS::getRedisPort);
            registry.add("spring.data.redis.password", () -> "");
        }
    }
}
