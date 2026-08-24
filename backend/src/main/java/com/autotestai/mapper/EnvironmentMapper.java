package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.EnvironmentEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EnvironmentMapper {

    String ENVIRONMENT_COLUMNS = "e.id, e.project_id AS projectId, e.name, e.base_url AS baseUrl, "
            + "e.headers_json AS headersJson, e.variables_json AS variablesJson, "
            + "e.created_at AS createdAt, e.updated_at AS updatedAt";

    @Select("SELECT " + ENVIRONMENT_COLUMNS
            + " FROM environments e"
            + " INNER JOIN projects p ON p.id = e.project_id"
            + " WHERE e.project_id = #{projectId} AND p.user_id = #{userId}"
            + " ORDER BY e.updated_at DESC, e.id DESC")
    List<EnvironmentEntity> findAllOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + ENVIRONMENT_COLUMNS
            + " FROM environments e"
            + " INNER JOIN projects p ON p.id = e.project_id"
            + " WHERE e.id = #{environmentId}"
            + " AND e.project_id = #{projectId}"
            + " AND p.user_id = #{userId}"
            + " LIMIT 1")
    Optional<EnvironmentEntity> findByIdOwned(
            @Param("environmentId") long environmentId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + ENVIRONMENT_COLUMNS
            + " FROM environments e"
            + " INNER JOIN projects p ON p.id = e.project_id"
            + " WHERE e.project_id = #{projectId}"
            + " AND e.name = #{name}"
            + " AND p.user_id = #{userId}"
            + " LIMIT 1")
    Optional<EnvironmentEntity> findByNameOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("name") String name);

    @Insert("""
            INSERT INTO environments (
                project_id, name, base_url, headers_json, variables_json
            )
            VALUES (
                #{projectId}, #{name}, #{baseUrl}, #{headersJson}, #{variablesJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(EnvironmentEntity environment);

    @Update("""
            UPDATE environments e
            INNER JOIN projects p ON p.id = e.project_id
            SET e.name = #{name},
                e.base_url = #{baseUrl},
                e.headers_json = #{headersJson},
                e.variables_json = #{variablesJson}
            WHERE e.id = #{environmentId}
              AND e.project_id = #{projectId}
              AND p.user_id = #{userId}
            """)
    int updateOwned(
            @Param("environmentId") long environmentId,
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("name") String name,
            @Param("baseUrl") String baseUrl,
            @Param("headersJson") String headersJson,
            @Param("variablesJson") String variablesJson);

    @Delete("""
            DELETE e
            FROM environments e
            INNER JOIN projects p ON p.id = e.project_id
            WHERE e.id = #{environmentId}
              AND e.project_id = #{projectId}
              AND p.user_id = #{userId}
            """)
    int deleteOwned(
            @Param("environmentId") long environmentId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);
}
