package com.taxdoc.planning;

import java.nio.file.Path;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests that need the full application and a real MySQL 8.4.
 *
 * <p>The container is set up exactly like docker compose: MySQL creates the {@code taxdoc}
 * schema, then the repo's own {@code db/init/02-planning-db.sh} creates the {@code planning}
 * schema and user. The application connects as {@code planning}, so these tests also prove the
 * init script works and that planning can't reach extraction's tables.
 *
 * <p>Skipped (not failed) when Docker isn't available.
 */
@SpringBootTest
@EnabledIf("com.taxdoc.planning.MySqlIntegrationTest#dockerAvailable")
public abstract class MySqlIntegrationTest {

    protected static final String PLANNING_PASSWORD = "planning-test";

    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("taxdoc")
            .withUsername("taxdoc")
            .withPassword("taxdoc-test")
            .withEnv("PLANNING_DB_PASSWORD", PLANNING_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "db", "init", "02-planning-db.sh")),
                    "/docker-entrypoint-initdb.d/02-planning-db.sh");

    static {
        if (dockerAvailable()) {
            MYSQL.start(); // shared by all test classes; Testcontainers stops it at JVM exit
        }
    }

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    protected static String planningJdbcUrl() {
        return "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/planning";
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlIntegrationTest::planningJdbcUrl);
        registry.add("spring.datasource.username", () -> "planning");
        registry.add("spring.datasource.password", () -> PLANNING_PASSWORD);
    }
}
