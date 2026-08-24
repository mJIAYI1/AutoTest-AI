ALTER TABLE test_suites
    ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 1 AFTER status;

ALTER TABLE test_results
    ADD COLUMN sequence_number INT UNSIGNED NOT NULL DEFAULT 1 AFTER api_id,
    ADD UNIQUE KEY uk_test_results_run_sequence (test_run_id, sequence_number);
