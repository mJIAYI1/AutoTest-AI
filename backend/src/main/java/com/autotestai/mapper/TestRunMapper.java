package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.ExtractedVariableEntity;
import com.autotestai.entity.TestResultEntity;
import com.autotestai.entity.TestReportSummaryEntity;
import com.autotestai.entity.TestRunEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TestRunMapper {

    String RUN_COLUMNS = "tr.id, tr.project_id AS projectId, tr.test_suite_id AS testSuiteId, "
            + "tr.environment_id AS environmentId, tr.triggered_by_user_id AS triggeredByUserId, "
            + "tr.run_type AS runType, tr.target_test_case_id AS targetTestCaseId, tr.status, "
            + "tr.total_count AS totalCount, tr.passed_count AS passedCount, "
            + "tr.failed_count AS failedCount, tr.error_count AS errorCount, "
            + "tr.error_message AS errorMessage, tr.started_at AS startedAt, "
            + "tr.finished_at AS finishedAt, tr.created_at AS createdAt";

    String RESULT_COLUMNS = "r.id, r.test_run_id AS testRunId, r.test_case_id AS testCaseId, "
            + "r.api_id AS apiId, r.sequence_number AS sequenceNumber, "
            + "a.method AS apiMethod, a.path AS apiPath, a.summary AS apiSummary, "
            + "tc.name AS testCaseName, r.status, r.request_url AS requestUrl, "
            + "r.request_method AS requestMethod, r.request_headers_json AS requestHeadersJson, "
            + "r.request_body AS requestBody, r.response_status AS responseStatus, "
            + "r.response_headers_json AS responseHeadersJson, r.response_body AS responseBody, "
            + "r.response_time_ms AS responseTimeMs, "
            + "r.assertion_results_json AS assertionResultsJson, r.error_message AS errorMessage, "
            + "r.executed_at AS executedAt";

    String REPORT_COLUMNS = "tr.id, tr.project_id AS projectId, "
            + "tr.test_suite_id AS testSuiteId, ts.name AS testSuiteName, "
            + "tr.target_test_case_id AS targetTestCaseId, target_tc.name AS testCaseName, "
            + "tr.environment_id AS environmentId, e.name AS environmentName, "
            + "tr.run_type AS runType, tr.status, tr.total_count AS totalCount, "
            + "tr.passed_count AS passedCount, tr.failed_count AS failedCount, "
            + "tr.error_count AS errorCount, metrics.averageResponseTimeMs, "
            + "tr.error_message AS errorMessage, tr.started_at AS startedAt, "
            + "tr.finished_at AS finishedAt, tr.created_at AS createdAt";

    String REPORT_JOINS = " FROM test_runs tr"
            + " INNER JOIN projects p ON p.id = tr.project_id"
            + " LEFT JOIN test_suites ts ON ts.id = tr.test_suite_id"
            + " LEFT JOIN test_cases target_tc ON target_tc.id = tr.target_test_case_id"
            + " LEFT JOIN environments e ON e.id = tr.environment_id"
            + " LEFT JOIN (SELECT test_run_id, ROUND(AVG(response_time_ms)) AS averageResponseTimeMs"
            + " FROM test_results WHERE response_time_ms IS NOT NULL GROUP BY test_run_id) metrics"
            + " ON metrics.test_run_id = tr.id";

    @Insert("""
            INSERT INTO test_runs (
                project_id, test_suite_id, environment_id, triggered_by_user_id,
                run_type, target_test_case_id, status, total_count
            )
            VALUES (
                #{projectId}, NULL, #{environmentId}, #{triggeredByUserId},
                'SINGLE_CASE', #{targetTestCaseId}, 'PENDING', 1
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSingle(TestRunEntity testRun);

    @Insert("""
            INSERT INTO test_runs (
                project_id, test_suite_id, environment_id, triggered_by_user_id,
                run_type, target_test_case_id, status, total_count
            )
            VALUES (
                #{projectId}, #{testSuiteId}, #{environmentId}, #{triggeredByUserId},
                'SUITE', NULL, 'PENDING', #{totalCount}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSuite(TestRunEntity testRun);

    @Select("SELECT " + RUN_COLUMNS
            + " FROM test_runs tr"
            + " INNER JOIN projects p ON p.id = tr.project_id"
            + " WHERE tr.id = #{runId} AND tr.project_id = #{projectId}"
            + " AND p.user_id = #{userId} LIMIT 1")
    Optional<TestRunEntity> findOwned(
            @Param("runId") long runId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + RESULT_COLUMNS
            + " FROM test_results r"
            + " INNER JOIN test_runs tr ON tr.id = r.test_run_id"
            + " INNER JOIN projects p ON p.id = tr.project_id"
            + " INNER JOIN test_cases tc ON tc.id = r.test_case_id"
            + " INNER JOIN apis a ON a.id = r.api_id"
            + " WHERE r.test_run_id = #{runId} AND tr.project_id = #{projectId}"
            + " AND p.user_id = #{userId} ORDER BY r.sequence_number, r.id")
    List<TestResultEntity> findResultsOwned(
            @Param("runId") long runId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + RESULT_COLUMNS
            + " FROM test_results r"
            + " INNER JOIN test_runs tr ON tr.id = r.test_run_id"
            + " INNER JOIN projects p ON p.id = tr.project_id"
            + " INNER JOIN test_cases tc ON tc.id = r.test_case_id"
            + " INNER JOIN apis a ON a.id = r.api_id"
            + " WHERE r.id = #{resultId} AND r.test_run_id = #{runId}"
            + " AND tr.project_id = #{projectId} AND p.user_id = #{userId} LIMIT 1")
    Optional<TestResultEntity> findResultOwned(
            @Param("resultId") long resultId,
            @Param("runId") long runId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + REPORT_COLUMNS + REPORT_JOINS
            + " WHERE tr.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY tr.created_at DESC, tr.id DESC LIMIT #{limit}")
    List<TestReportSummaryEntity> findReportSummariesOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("limit") int limit);

    @Select("SELECT " + REPORT_COLUMNS + REPORT_JOINS
            + " WHERE tr.id = #{runId} AND tr.project_id = #{projectId}"
            + " AND p.user_id = #{userId} LIMIT 1")
    Optional<TestReportSummaryEntity> findReportSummaryOwned(
            @Param("runId") long runId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT id, test_run_id AS testRunId, test_result_id AS testResultId, "
            + "name, value_text AS valueText, source_expression AS sourceExpression"
            + " FROM extracted_variables WHERE test_result_id = #{resultId} ORDER BY id")
    List<ExtractedVariableEntity> findExtractedByResultId(@Param("resultId") long resultId);

    @Update("UPDATE test_runs SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP(3) "
            + "WHERE id = #{runId} AND status = 'PENDING'")
    int markRunning(@Param("runId") long runId);

    @Insert("""
            INSERT INTO test_results (
                test_run_id, test_case_id, api_id, sequence_number, status,
                request_url, request_method, request_headers_json, request_body,
                response_status, response_headers_json, response_body, response_time_ms,
                assertion_results_json, error_message
            )
            VALUES (
                #{testRunId}, #{testCaseId}, #{apiId}, #{sequenceNumber}, #{status},
                #{requestUrl}, #{requestMethod}, #{requestHeadersJson}, #{requestBody},
                #{responseStatus}, #{responseHeadersJson}, #{responseBody}, #{responseTimeMs},
                #{assertionResultsJson}, #{errorMessage}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertResult(TestResultEntity result);

    @Insert("""
            INSERT INTO extracted_variables (
                test_run_id, test_result_id, name, value_text, source_expression
            )
            VALUES (#{testRunId}, #{testResultId}, #{name}, #{valueText}, #{sourceExpression})
            ON DUPLICATE KEY UPDATE
                test_result_id = VALUES(test_result_id),
                value_text = VALUES(value_text),
                source_expression = VALUES(source_expression)
            """)
    int insertExtracted(ExtractedVariableEntity variable);

    @Update("""
            UPDATE test_runs
            SET status = #{status},
                passed_count = #{passedCount},
                failed_count = #{failedCount},
                error_count = #{errorCount},
                error_message = #{errorMessage},
                finished_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{runId} AND status IN ('PENDING', 'RUNNING')
            """)
    int finish(
            @Param("runId") long runId,
            @Param("status") String status,
            @Param("passedCount") int passedCount,
            @Param("failedCount") int failedCount,
            @Param("errorCount") int errorCount,
            @Param("errorMessage") String errorMessage);
}
