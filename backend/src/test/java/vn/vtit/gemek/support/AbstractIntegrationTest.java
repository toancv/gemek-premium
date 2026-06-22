/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.support;

import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for every {@code @SpringBootTest} integration test.
 *
 * <p>Starts a single, shared (singleton) PostgreSQL and Redis container for the
 * whole test JVM and rebinds the datasource and Redis properties at context-build
 * time via {@link DynamicPropertySource}. This isolates the suite from the shared
 * Docker dev database (previously {@code localhost:5433/gemek}, ~2k polluted rows),
 * so Flyway builds a fresh schema (V1..V17) and {@code AdminSeeder} seeds the test
 * admin on an empty DB.
 *
 * <p>Container image tags match the dev/prod compose stack
 * ({@code postgres:15.18-alpine}, {@code redis:7.2.4-alpine}) for migration parity.
 *
 * <p>The containers are started once in a static initializer and reused across all
 * subclasses (Testcontainers/Ryuk tears them down at JVM exit). They are deliberately
 * NOT JUnit {@code @Container}-managed, which would restart per class and be slow.
 */
public abstract class AbstractIntegrationTest {

    /** Shared PostgreSQL container — image tag matches the dev/prod stack. */
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.18-alpine"))
                    .withDatabaseName("gemek")
                    .withUsername("gemek")
                    .withPassword("test");

    /** Shared Redis container (no auth) — image tag matches the dev/prod stack. */
    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:7.2.4-alpine"));

    static {
        // Start once for the whole JVM; reused across all subclasses for speed.
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Rebinds datasource and Redis properties to the running containers.
     *
     * <p>{@code @DynamicPropertySource} values take precedence over
     * {@code application-test.yml}, so the hardcoded {@code localhost:5433/6380}
     * targets are overridden. Redis password is set blank — the container runs
     * without {@code requirepass}, and Lettuce sends no AUTH for an empty password.
     *
     * @param registry the Spring dynamic property registry.
     */
    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getRedisHost);
        registry.add("spring.data.redis.port", REDIS::getRedisPort);
        registry.add("spring.data.redis.password", () -> "");
    }
}
