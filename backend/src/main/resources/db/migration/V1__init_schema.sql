SET NAMES utf8mb4;

CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE projects (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    base_url VARCHAR(2048) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_projects_user_name (user_id, name),
    KEY idx_projects_user_updated (user_id, updated_at),
    CONSTRAINT fk_projects_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE environments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    headers_json JSON NULL,
    variables_json JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_environments_project_name (project_id, name),
    CONSTRAINT fk_environments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE apis (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    operation_id VARCHAR(255) NULL,
    method VARCHAR(12) NOT NULL,
    path VARCHAR(512) NOT NULL,
    summary VARCHAR(500) NULL,
    description TEXT NULL,
    tags_json JSON NULL,
    parameters_json JSON NULL,
    request_schema_json JSON NULL,
    response_schema_json JSON NULL,
    security_json JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_apis_project_method_path (project_id, method, path),
    KEY idx_apis_project_operation (project_id, operation_id),
    CONSTRAINT fk_apis_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_cases (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    api_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    request_headers_json JSON NULL,
    request_body_json JSON NULL,
    assertions_json JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INT UNSIGNED NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_test_cases_api_enabled (api_id, enabled),
    CONSTRAINT chk_test_cases_type CHECK (type IN (
        'NORMAL', 'BOUNDARY', 'NEGATIVE', 'MISSING_PARAMETER', 'INVALID_TYPE', 'AUTHENTICATION'
    )),
    CONSTRAINT fk_test_cases_api FOREIGN KEY (api_id) REFERENCES apis (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_suites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    stop_on_failure BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_suites_project_name (project_id, name),
    CONSTRAINT chk_test_suites_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_test_suites_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_suite_cases (
    test_suite_id BIGINT UNSIGNED NOT NULL,
    test_case_id BIGINT UNSIGNED NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (test_suite_id, test_case_id),
    UNIQUE KEY uk_test_suite_cases_order (test_suite_id, sort_order),
    CONSTRAINT fk_test_suite_cases_suite FOREIGN KEY (test_suite_id) REFERENCES test_suites (id) ON DELETE CASCADE,
    CONSTRAINT fk_test_suite_cases_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_runs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    test_suite_id BIGINT UNSIGNED NOT NULL,
    environment_id BIGINT UNSIGNED NOT NULL,
    triggered_by_user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    passed_count INT UNSIGNED NOT NULL DEFAULT 0,
    failed_count INT UNSIGNED NOT NULL DEFAULT 0,
    error_count INT UNSIGNED NOT NULL DEFAULT 0,
    started_at TIMESTAMP(3) NULL,
    finished_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_test_runs_project_created (project_id, created_at),
    KEY idx_test_runs_suite_created (test_suite_id, created_at),
    CONSTRAINT chk_test_runs_status CHECK (status IN (
        'PENDING', 'RUNNING', 'PASS', 'FAIL', 'ERROR', 'CANCELLED'
    )),
    CONSTRAINT fk_test_runs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_runs_suite FOREIGN KEY (test_suite_id) REFERENCES test_suites (id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_runs_environment FOREIGN KEY (environment_id) REFERENCES environments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_runs_user FOREIGN KEY (triggered_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_results (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT UNSIGNED NOT NULL,
    test_case_id BIGINT UNSIGNED NOT NULL,
    api_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_url VARCHAR(2048) NOT NULL,
    request_method VARCHAR(12) NOT NULL,
    request_headers_json JSON NULL,
    request_body LONGTEXT NULL,
    response_status INT NULL,
    response_headers_json JSON NULL,
    response_body LONGTEXT NULL,
    response_time_ms BIGINT UNSIGNED NULL,
    assertion_results_json JSON NULL,
    error_message TEXT NULL,
    executed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_test_results_run_status (test_run_id, status),
    KEY idx_test_results_case (test_case_id),
    CONSTRAINT chk_test_results_status CHECK (status IN ('PASS', 'FAIL', 'ERROR')),
    CONSTRAINT fk_test_results_run FOREIGN KEY (test_run_id) REFERENCES test_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_test_results_case FOREIGN KEY (test_case_id) REFERENCES test_cases (id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_results_api FOREIGN KEY (api_id) REFERENCES apis (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE extracted_variables (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    test_run_id BIGINT UNSIGNED NOT NULL,
    test_result_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    value_text LONGTEXT NULL,
    source_expression VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_extracted_variables_run_name (test_run_id, name),
    CONSTRAINT fk_extracted_variables_run FOREIGN KEY (test_run_id) REFERENCES test_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_extracted_variables_result FOREIGN KEY (test_result_id) REFERENCES test_results (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
