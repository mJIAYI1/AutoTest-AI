CREATE TABLE ai_failure_diagnoses (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    test_result_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(40) NOT NULL,
    model VARCHAR(160) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    possible_causes_json JSON NOT NULL,
    check_locations_json JSON NOT NULL,
    repair_suggestions_json JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_failure_diagnoses_result (test_result_id),
    CONSTRAINT chk_ai_failure_diagnoses_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_ai_failure_diagnoses_result FOREIGN KEY (test_result_id)
        REFERENCES test_results (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
