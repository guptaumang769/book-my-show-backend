package com.umang.bookmyshow;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context against a throwaway Postgres, which forces Flyway to
 * apply V1 and Hibernate to validate every entity mapping against the resulting schema.
 * If an entity and its table drift apart, context startup fails — so this one test is
 * the Day 1 end-to-end proof that the schema and the JPA model agree.
 */
@Testcontainers
@SpringBootTest
class SchemaMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("bookmyshow")
                    .withUsername("bms")
                    .withPassword("bms");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No Redis needed for the schema check; point it somewhere harmless.
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void allTablesCreatedByFlyway() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer tableCount = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class);
        // 11 domain tables + flyway_schema_history.
        assertThat(tableCount).isGreaterThanOrEqualTo(12);

        Integer bookingCols = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'bookings'",
                Integer.class);
        assertThat(bookingCols).isEqualTo(14);
    }
}
