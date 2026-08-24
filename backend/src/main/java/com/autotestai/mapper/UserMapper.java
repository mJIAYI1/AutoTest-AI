package com.autotestai.mapper;

import java.util.Optional;

import com.autotestai.entity.UserEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    String USER_COLUMNS = "id, username, email, "
            + "password_hash AS passwordHash, "
            + "display_name AS displayName, "
            + "status, "
            + "created_at AS createdAt, "
            + "updated_at AS updatedAt";

    @Select("SELECT " + USER_COLUMNS + " FROM users WHERE id = #{id} LIMIT 1")
    Optional<UserEntity> findById(@Param("id") long id);

    @Select("SELECT " + USER_COLUMNS + " FROM users WHERE username = #{username} LIMIT 1")
    Optional<UserEntity> findByUsername(@Param("username") String username);

    @Select("SELECT " + USER_COLUMNS + " FROM users WHERE email = #{email} LIMIT 1")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Insert("""
            INSERT INTO users (username, email, password_hash, display_name, status)
            VALUES (#{username}, #{email}, #{passwordHash}, #{displayName}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(UserEntity user);

    @Update("""
            UPDATE users
            SET email = #{email}, display_name = #{displayName}
            WHERE id = #{id}
            """)
    int updateProfile(
            @Param("id") long id,
            @Param("email") String email,
            @Param("displayName") String displayName);
}
