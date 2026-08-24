ALTER TABLE test_runs
    MODIFY COLUMN test_suite_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN environment_id BIGINT UNSIGNED NULL,
    ADD COLUMN run_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE_CASE' AFTER triggered_by_user_id,
    ADD COLUMN target_test_case_id BIGINT UNSIGNED NULL AFTER run_type,
    ADD COLUMN error_message TEXT NULL AFTER error_count,
    ADD KEY idx_test_runs_target_case (target_test_case_id),
    ADD CONSTRAINT chk_test_runs_type CHECK (run_type IN ('SINGLE_CASE', 'SUITE')),
    ADD CONSTRAINT chk_test_runs_target CHECK (
        (run_type = 'SINGLE_CASE' AND target_test_case_id IS NOT NULL AND test_suite_id IS NULL)
        OR (run_type = 'SUITE' AND target_test_case_id IS NULL AND test_suite_id IS NOT NULL)
    ),
    ADD CONSTRAINT fk_test_runs_target_case FOREIGN KEY (target_test_case_id)
        REFERENCES test_cases (id) ON DELETE RESTRICT;
