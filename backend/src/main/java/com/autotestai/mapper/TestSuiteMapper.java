package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.TestSuiteCaseEntity;
import com.autotestai.entity.TestSuiteEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TestSuiteMapper {

    String SUITE_COLUMNS = "ts.id, ts.project_id AS projectId, ts.name, ts.description, "
            + "ts.stop_on_failure AS stopOnFailure, ts.status, ts.version, "
            + "ts.created_at AS createdAt, ts.updated_at AS updatedAt";

    String CASE_COLUMNS = "tsc.test_suite_id AS testSuiteId, tsc.test_case_id AS testCaseId, "
            + "tsc.sort_order AS sortOrder, tsc.enabled, tc.name AS testCaseName, "
            + "tc.enabled AS testCaseEnabled, tc.api_id AS apiId, a.method, a.path";

    String CANDIDATE_COLUMNS = "NULL AS testSuiteId, tc.id AS testCaseId, 0 AS sortOrder, "
            + "TRUE AS enabled, tc.name AS testCaseName, tc.enabled AS testCaseEnabled, "
            + "tc.api_id AS apiId, a.method, a.path";

    @Insert("""
            INSERT INTO test_suites (
                project_id, name, description, stop_on_failure, status
            )
            VALUES (#{projectId}, #{name}, #{description}, #{stopOnFailure}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TestSuiteEntity suite);

    @Select("SELECT " + SUITE_COLUMNS
            + " FROM test_suites ts"
            + " INNER JOIN projects p ON p.id = ts.project_id"
            + " WHERE ts.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY ts.updated_at DESC, ts.id DESC")
    List<TestSuiteEntity> findAllOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + SUITE_COLUMNS
            + " FROM test_suites ts"
            + " INNER JOIN projects p ON p.id = ts.project_id"
            + " WHERE ts.id = #{suiteId} AND ts.project_id = #{projectId}"
            + " AND p.user_id = #{userId} LIMIT 1")
    Optional<TestSuiteEntity> findByIdOwned(
            @Param("suiteId") long suiteId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + SUITE_COLUMNS
            + " FROM test_suites ts"
            + " INNER JOIN projects p ON p.id = ts.project_id"
            + " WHERE ts.project_id = #{projectId} AND ts.name = #{name}"
            + " AND p.user_id = #{userId} LIMIT 1")
    Optional<TestSuiteEntity> findByNameOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("name") String name);

    @Update("""
            UPDATE test_suites ts
            INNER JOIN projects p ON p.id = ts.project_id
            SET ts.name = #{suite.name},
                ts.description = #{suite.description},
                ts.stop_on_failure = #{suite.stopOnFailure},
                ts.version = ts.version + 1
            WHERE ts.id = #{suiteId}
              AND ts.project_id = #{projectId}
              AND p.user_id = #{userId}
              AND ts.version = #{expectedVersion}
            """)
    int updateOwned(
            @Param("suiteId") long suiteId,
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("expectedVersion") int expectedVersion,
            @Param("suite") TestSuiteEntity suite);

    @Delete("""
            DELETE ts
            FROM test_suites ts
            INNER JOIN projects p ON p.id = ts.project_id
            WHERE ts.id = #{suiteId}
              AND ts.project_id = #{projectId}
              AND p.user_id = #{userId}
            """)
    int deleteOwned(
            @Param("suiteId") long suiteId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Insert("""
            INSERT INTO test_suite_cases (
                test_suite_id, test_case_id, sort_order, enabled
            )
            VALUES (#{testSuiteId}, #{testCaseId}, #{sortOrder}, #{enabled})
            """)
    int insertCase(TestSuiteCaseEntity suiteCase);

    @Delete("DELETE FROM test_suite_cases WHERE test_suite_id = #{suiteId}")
    int deleteCases(@Param("suiteId") long suiteId);

    @Select("SELECT " + CASE_COLUMNS
            + " FROM test_suite_cases tsc"
            + " INNER JOIN test_suites ts ON ts.id = tsc.test_suite_id"
            + " INNER JOIN projects p ON p.id = ts.project_id"
            + " INNER JOIN test_cases tc ON tc.id = tsc.test_case_id"
            + " INNER JOIN apis a ON a.id = tc.api_id AND a.project_id = ts.project_id"
            + " WHERE tsc.test_suite_id = #{suiteId}"
            + " AND ts.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY tsc.sort_order")
    List<TestSuiteCaseEntity> findCasesOwned(
            @Param("suiteId") long suiteId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + CANDIDATE_COLUMNS
            + " FROM test_cases tc"
            + " INNER JOIN apis a ON a.id = tc.api_id"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE tc.id = #{testCaseId} AND a.project_id = #{projectId}"
            + " AND p.user_id = #{userId} LIMIT 1")
    Optional<TestSuiteCaseEntity> findCandidateOwned(
            @Param("testCaseId") long testCaseId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + CANDIDATE_COLUMNS
            + " FROM test_cases tc"
            + " INNER JOIN apis a ON a.id = tc.api_id"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE a.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY a.path, a.method, tc.updated_at DESC, tc.id DESC")
    List<TestSuiteCaseEntity> findCandidatesOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId);
}
