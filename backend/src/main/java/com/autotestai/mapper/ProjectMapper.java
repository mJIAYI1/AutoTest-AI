package com.autotestai.mapper;

import java.util.List;
import java.util.Optional;

import com.autotestai.entity.ProjectEntity;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectMapper {

    String PROJECT_COLUMNS = "id, user_id AS userId, name, description, base_url AS baseUrl, "
            + "created_at AS createdAt, updated_at AS updatedAt";

    @Select("SELECT " + PROJECT_COLUMNS
            + " FROM projects"
            + " WHERE user_id = #{userId}"
            + " ORDER BY updated_at DESC, id DESC")
    List<ProjectEntity> findAllByUserId(@Param("userId") long userId);

    @Select("SELECT " + PROJECT_COLUMNS
            + " FROM projects"
            + " WHERE id = #{projectId} AND user_id = #{userId}"
            + " LIMIT 1")
    Optional<ProjectEntity> findByIdAndUserId(
            @Param("projectId") long projectId,
            @Param("userId") long userId);

    @Select("SELECT " + PROJECT_COLUMNS
            + " FROM projects"
            + " WHERE user_id = #{userId} AND name = #{name}"
            + " LIMIT 1")
    Optional<ProjectEntity> findByUserIdAndName(
            @Param("userId") long userId,
            @Param("name") String name);

    @Insert("""
            INSERT INTO projects (user_id, name, description, base_url)
            VALUES (#{userId}, #{name}, #{description}, #{baseUrl})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ProjectEntity project);

    @Update("""
            UPDATE projects
            SET name = #{name}, description = #{description}, base_url = #{baseUrl}
            WHERE id = #{projectId} AND user_id = #{userId}
            """)
    int updateOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("name") String name,
            @Param("description") String description,
            @Param("baseUrl") String baseUrl);

    @Delete("DELETE FROM projects WHERE id = #{projectId} AND user_id = #{userId}")
    int deleteOwned(
            @Param("projectId") long projectId,
            @Param("userId") long userId);
}
