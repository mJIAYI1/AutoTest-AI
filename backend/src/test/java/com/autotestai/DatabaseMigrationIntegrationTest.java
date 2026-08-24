package com.autotestai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class DatabaseMigrationIntegrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "ai_failure_diagnoses",
            "apis",
            "environments",
            "extracted_variables",
            "projects",
            "test_cases",
            "test_results",
            "test_runs",
            "test_suite_cases",
            "test_suites",
            "users");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("autotest_ai_test")
            .withUsername("autotest_test")
            .withPassword("autotest_test_password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAllCoreTables() {
        List<String> actualTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);

        assertThat(actualTables).containsExactlyElementsOf(EXPECTED_TABLES);
    }

    @Test
    void flywayRecordsVersionOneAsSuccessful() {
        Integer success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '1'",
                Integer.class);

        assertThat(success).isEqualTo(1);
    }

    @Test
    void flywayExtendsTestCaseConfigurationInVersionTwo() {
        Integer success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '2'",
                Integer.class);
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'test_cases'
                """,
                String.class);

        assertThat(success).isEqualTo(1);
        assertThat(columns).contains(
                "description",
                "request_path_parameters_json",
                "request_query_parameters_json",
                "extraction_rules_json");
    }

    @Test
    void flywaySupportsSingleCaseRunsInVersionThree() {
        Integer success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '3'",
                Integer.class);
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'test_runs'
                """,
                String.class);

        assertThat(success).isEqualTo(1);
        assertThat(columns).contains("run_type", "target_test_case_id", "error_message");
    }

    @Test
    void flywaySupportsOrderedSuiteResultsInVersionFour() {
        Integer success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '4'",
                Integer.class);
        List<String> resultColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'test_results'
                """,
                String.class);
        List<String> suiteColumns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'test_suites'
                """,
                String.class);

        assertThat(success).isEqualTo(1);
        assertThat(resultColumns).contains("sequence_number");
        assertThat(suiteColumns).contains("version");
    }

    @Test
    void flywayPersistsStructuredFailureDiagnosesInVersionFive() {
        Integer success = jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5'",
                Integer.class);
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'ai_failure_diagnoses'
                """,
                String.class);

        assertThat(success).isEqualTo(1);
        assertThat(columns).contains(
                "test_result_id",
                "provider",
                "model",
                "summary",
                "severity",
                "possible_causes_json",
                "check_locations_json",
                "repair_suggestions_json");
    }
}
