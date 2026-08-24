package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.ApiEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApiMapper {

    String API_COLUMNS = "a.id, a.project_id AS projectId, a.operation_id AS operationId, "
            + "a.method, a.path, a.summary, a.description, a.tags_json AS tagsJson, "
            + "a.parameters_json AS parametersJson, a.request_schema_json AS requestSchemaJson, "
            + "a.response_schema_json AS responseSchemaJson, a.security_json AS securityJson, "
            + "a.created_at AS createdAt, a.updated_at AS updatedAt";

    @Select("SELECT " + API_COLUMNS
            + " FROM apis a"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE a.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY a.path ASC, a.method ASC, a.id ASC")
    List<ApiEntity> findAllOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + API_COLUMNS
            + " FROM apis a"
            + " INNER JOIN projects p ON p.id = a.project_id"
            + " WHERE a.id = #{apiId}"
            + " AND a.project_id = #{projectId}"
            + " AND p.user_id = #{userId}"
            + " LIMIT 1")
    Optional<ApiEntity> findByIdOwned(
            @Param("apiId") long apiId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Insert("""
            INSERT INTO apis (
                project_id, operation_id, method, path, summary, description,
                tags_json, parameters_json, request_schema_json,
                response_schema_json, security_json
            )
            VALUES (
                #{projectId}, #{operationId}, #{method}, #{path}, #{summary}, #{description},
                #{tagsJson}, #{parametersJson}, #{requestSchemaJson},
                #{responseSchemaJson}, #{securityJson}
            )
            ON DUPLICATE KEY UPDATE
                operation_id = VALUES(operation_id),
                summary = VALUES(summary),
                description = VALUES(description),
                tags_json = VALUES(tags_json),
                parameters_json = VALUES(parameters_json),
                request_schema_json = VALUES(request_schema_json),
                response_schema_json = VALUES(response_schema_json),
                security_json = VALUES(security_json),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int upsert(ApiEntity api);
}
