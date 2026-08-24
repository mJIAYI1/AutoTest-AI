ALTER TABLE test_cases
    ADD COLUMN description VARCHAR(4000) NULL AFTER name,
    ADD COLUMN request_path_parameters_json JSON NULL AFTER request_headers_json,
    ADD COLUMN request_query_parameters_json JSON NULL AFTER request_path_parameters_json,
    ADD COLUMN extraction_rules_json JSON NOT NULL DEFAULT (JSON_ARRAY()) AFTER assertions_json,
    ADD CONSTRAINT uk_test_cases_api_name UNIQUE (api_id, name);
