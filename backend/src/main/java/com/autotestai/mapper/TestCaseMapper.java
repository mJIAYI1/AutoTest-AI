package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.TestCaseEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TestCaseMapper {

    String TEST_CASE_COLUMNS = "tc.id, tc.api_id AS apiId, tc.name, tc.description, tc.type, "
            + "tc.request_headers_json AS requestHeadersJson, "
            + "tc.request_path_parameters_json AS pathParametersJson, "
            + "tc.request_query_parameters_json AS queryParametersJson, "
            + "tc.request_body_json AS requestBodyJson, tc.assertions_json AS assertionsJson, "
            + "tc.extraction_rules_json AS extractionRulesJson, tc.enabled, tc.version, "
            + "tc.created_at AS createdAt, tc.updated_at AS updatedAt";

    @Select("SELECT " + TEST_CASE_COLUMNS
            + " FROM test_cases tc"
            + " INNER JOIN apis a ON a.id = tc.api_id"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE tc.api_id = #{apiId}"
            + " AND a.project_id = #{projectId}"
            + " AND p.user_id = #{userId}"
            + " ORDER BY tc.updated_at DESC, tc.id DESC")
    List<TestCaseEntity> findAllOwned(
            @Param("projectId") long projectId,
            @Param("apiId") long apiId,
            @Param("userId") long userId);

    @Select("SELECT " + TEST_CASE_COLUMNS
            + " FROM test_cases tc"
            + " INNER JOIN apis a ON a.id = tc.api_id"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE tc.id = #{testCaseId}"
            + " AND tc.api_id = #{apiId}"
            + " AND a.project_id = #{projectId}"
            + " AND p.user_id = #{userId}"
            + " LIMIT 1")
    Optional<TestCaseEntity> findByIdOwned(
            @Param("testCaseId") long testCaseId,
            @Param("projectId") long projectId,
            @Param("apiId") long apiId,
            @Param("userId") long userId);

    @Select("SELECT " + TEST_CASE_COLUMNS
            + " FROM test_cases tc"
            + " INNER JOIN apis a ON a.id = tc.api_id"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE tc.api_id = #{apiId}"
            + " AND tc.name = #{name}"
            + " AND a.project_id = #{projectId}"
            + " AND p.user_id = #{userId}"
            + " LIMIT 1")
    Optional<TestCaseEntity> findByNameOwned(
            @Param("projectId") long projectId,
            @Param("apiId") long apiId,
            @Param("userId") long userId,
            @Param("name") String name);

    @Insert("""
            INSERT INTO test_cases (
                api_id, name, description, type, request_headers_json,
                request_path_parameters_json, request_query_parameters_json,
                request_body_json, assertions_json, extraction_rules_json, enabled
            )
            VALUES (
                #{apiId}, #{name}, #{description}, #{type}, #{requestHeadersJson},
                #{pathParametersJson}, #{queryParametersJson},
                #{requestBodyJson}, #{assertionsJson}, #{extractionRulesJson}, #{enabled}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(TestCaseEntity testCase);

    @Update("""
            UPDATE test_cases tc
            INNER JOIN apis a ON a.id = tc.api_id
            INNER JOIN projects p ON p.id = a.project_id
            SET tc.name = #{testCase.name},
                tc.description = #{testCase.description},
                tc.type = #{testCase.type},
                tc.request_headers_json = #{testCase.requestHeadersJson},
                tc.request_path_parameters_json = #{testCase.pathParametersJson},
                tc.request_query_parameters_json = #{testCase.queryParametersJson},
                tc.request_body_json = #{testCase.requestBodyJson},
                tc.assertions_json = #{testCase.assertionsJson},
                tc.extraction_rules_json = #{testCase.extractionRulesJson},
                tc.enabled = #{testCase.enabled},
                tc.version = tc.version + 1
            WHERE tc.id = #{testCaseId}
              AND tc.api_id = #{apiId}
              AND a.project_id = #{projectId}
              AND p.user_id = #{userId}
              AND tc.version = #{expectedVersion}
            """)
    int updateOwned(
            @Param("testCaseId") long testCaseId,
            @Param("projectId") long projectId,
            @Param("apiId") long apiId,
            @Param("userId") long userId,
            @Param("expectedVersion") int expectedVersion,
            @Param("testCase") TestCaseEntity testCase);

    @Delete("""
            DELETE tc
            FROM test_cases tc
            INNER JOIN apis a ON a.id = tc.api_id
            INNER JOIN projects p ON p.id = a.project_id
            WHERE tc.id = #{testCaseId}
              AND tc.api_id = #{apiId}
              AND a.project_id = #{projectId}
              AND p.user_id = #{userId}
            """)
    int deleteOwned(
            @Param("testCaseId") long testCaseId,
            @Param("projectId") long projectId,
            @Param("apiId") long apiId,
            @Param("userId") long userId);
}
