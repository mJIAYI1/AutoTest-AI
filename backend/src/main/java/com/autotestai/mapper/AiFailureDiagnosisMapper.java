package com.autotestai.mapper;

import java.util.Optional;

import com.autotestai.entity.AiFailureDiagnosisEntity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiFailureDiagnosisMapper {

    String DIAGNOSIS_COLUMNS = "d.id, d.test_result_id AS testResultId, d.provider, d.model, "
            + "d.summary, d.severity, d.possible_causes_json AS possibleCausesJson, "
            + "d.check_locations_json AS checkLocationsJson, "
            + "d.repair_suggestions_json AS repairSuggestionsJson, "
            + "d.created_at AS createdAt, d.updated_at AS updatedAt";

    @Insert("""
            INSERT INTO ai_failure_diagnoses (
                test_result_id, provider, model, summary, severity,
                possible_causes_json, check_locations_json, repair_suggestions_json
            )
            VALUES (
                #{testResultId}, #{provider}, #{model}, #{summary}, #{severity},
                #{possibleCausesJson}, #{checkLocationsJson}, #{repairSuggestionsJson}
            )
            ON DUPLICATE KEY UPDATE
                id = LAST_INSERT_ID(id),
                provider = VALUES(provider),
                model = VALUES(model),
                summary = VALUES(summary),
                severity = VALUES(severity),
                possible_causes_json = VALUES(possible_causes_json),
                check_locations_json = VALUES(check_locations_json),
                repair_suggestions_json = VALUES(repair_suggestions_json),
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int upsert(AiFailureDiagnosisEntity diagnosis);

    @Select("SELECT " + DIAGNOSIS_COLUMNS
            + " FROM ai_failure_diagnoses d"
            + " INNER JOIN test_results r ON r.id = d.test_result_id"
            + " INNER JOIN test_runs tr ON tr.id = r.test_run_id"
            + " INNER JOIN projects p ON p.id = tr.project_id"
            + " WHERE d.test_result_id = #{resultId} AND r.test_run_id = #{runId}"
            + " AND tr.project_id = #{projectId} AND p.user_id = #{userId} LIMIT 1")
    Optional<AiFailureDiagnosisEntity> findOwned(
            @Param("resultId") long resultId,
            @Param("runId") long runId,
            @Param("projectId") long projectId,
            @Param("userId") long userId);
}
