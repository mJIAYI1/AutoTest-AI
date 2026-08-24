package com.autotestai.mapper;

import java.util.List;

import com.autotestai.entity.DashboardStatisticsEntity;
import com.autotestai.entity.DailyPassRateEntity;
import com.autotestai.entity.DailyResponseTimeEntity;
import com.autotestai.entity.FailingApiStatisticsEntity;
import com.autotestai.entity.RecentFailureEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT
                (SELECT COUNT(*)
                   FROM projects p1
                  WHERE p1.user_id = #{userId}) AS projectCount,
                (SELECT COUNT(*)
                   FROM apis a1
                   INNER JOIN projects p2 ON p2.id = a1.project_id
                  WHERE p2.user_id = #{userId}) AS apiCount,
                (SELECT COUNT(*)
                   FROM test_cases tc1
                   INNER JOIN apis a2 ON a2.id = tc1.api_id
                   INNER JOIN projects p3 ON p3.id = a2.project_id
                  WHERE p3.user_id = #{userId}) AS testCaseCount,
                (SELECT COUNT(*)
                   FROM test_runs tr1
                   INNER JOIN projects p4 ON p4.id = tr1.project_id
                  WHERE p4.user_id = #{userId}
                    AND tr1.created_at >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 7 DAY))
                    AS recentRunCount,
                (SELECT COALESCE(SUM(tr2.passed_count), 0)
                   FROM test_runs tr2
                   INNER JOIN projects p5 ON p5.id = tr2.project_id
                  WHERE p5.user_id = #{userId}
                    AND tr2.status IN ('PASS', 'FAIL', 'ERROR')) AS totalPassedCount,
                (SELECT COALESCE(SUM(tr3.total_count), 0)
                   FROM test_runs tr3
                   INNER JOIN projects p6 ON p6.id = tr3.project_id
                  WHERE p6.user_id = #{userId}
                    AND tr3.status IN ('PASS', 'FAIL', 'ERROR')) AS totalPlannedCount
            """)
    DashboardStatisticsEntity findStatistics(@Param("userId") long userId);

    @Select("""
            SELECT r.id AS resultId,
                   tr.id AS runId,
                   p.id AS projectId,
                   p.name AS projectName,
                   a.id AS apiId,
                   a.method,
                   a.path,
                   a.summary AS apiSummary,
                   tc.id AS testCaseId,
                   tc.name AS testCaseName,
                   r.status,
                   r.response_status AS responseStatus,
                   r.response_time_ms AS responseTimeMs,
                   r.error_message AS errorMessage,
                   r.executed_at AS executedAt
              FROM test_results r
              INNER JOIN test_runs tr ON tr.id = r.test_run_id
              INNER JOIN projects p ON p.id = tr.project_id
              INNER JOIN apis a ON a.id = r.api_id
              INNER JOIN test_cases tc ON tc.id = r.test_case_id
             WHERE p.user_id = #{userId}
               AND r.status IN ('FAIL', 'ERROR')
             ORDER BY r.executed_at DESC, r.id DESC
             LIMIT #{limit}
            """)
    List<RecentFailureEntity> findRecentFailures(
            @Param("userId") long userId,
            @Param("limit") int limit);

    @Select("""
            SELECT DATE(tr.created_at) AS metricDate,
                   SUM(tr.passed_count) AS passedCount,
                   SUM(tr.total_count) AS totalCount
              FROM test_runs tr
              INNER JOIN projects p ON p.id = tr.project_id
             WHERE p.user_id = #{userId}
               AND tr.status IN ('PASS', 'FAIL', 'ERROR')
               AND tr.created_at >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)
             GROUP BY DATE(tr.created_at)
             ORDER BY metricDate
            """)
    List<DailyPassRateEntity> findDailyPassRates(@Param("userId") long userId);

    @Select("""
            SELECT DATE(r.executed_at) AS metricDate,
                   COUNT(r.response_time_ms) AS sampleCount,
                   ROUND(AVG(r.response_time_ms)) AS averageResponseTimeMs
              FROM test_results r
              INNER JOIN test_runs tr ON tr.id = r.test_run_id
              INNER JOIN projects p ON p.id = tr.project_id
             WHERE p.user_id = #{userId}
               AND r.response_time_ms IS NOT NULL
               AND r.executed_at >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)
             GROUP BY DATE(r.executed_at)
             ORDER BY metricDate
            """)
    List<DailyResponseTimeEntity> findDailyResponseTimes(@Param("userId") long userId);

    @Select("""
            SELECT a.id AS apiId,
                   p.id AS projectId,
                   p.name AS projectName,
                   a.method,
                   a.path,
                   a.summary,
                   SUM(CASE WHEN r.status IN ('FAIL', 'ERROR') THEN 1 ELSE 0 END) AS failureCount,
                   COUNT(*) AS executionCount,
                   ROUND(100.0 * SUM(CASE WHEN r.status IN ('FAIL', 'ERROR') THEN 1 ELSE 0 END)
                         / COUNT(*), 2) AS failureRate
              FROM test_results r
              INNER JOIN test_runs tr ON tr.id = r.test_run_id
              INNER JOIN projects p ON p.id = tr.project_id
              INNER JOIN apis a ON a.id = r.api_id
             WHERE p.user_id = #{userId}
               AND r.executed_at >= DATE_SUB(CURRENT_DATE, INTERVAL 6 DAY)
             GROUP BY a.id, p.id, p.name, a.method, a.path, a.summary
            HAVING SUM(CASE WHEN r.status IN ('FAIL', 'ERROR') THEN 1 ELSE 0 END) > 0
             ORDER BY failureCount DESC, failureRate DESC, a.id
             LIMIT #{limit}
            """)
    List<FailingApiStatisticsEntity> findTopFailingApis(
            @Param("userId") long userId,
            @Param("limit") int limit);
}
