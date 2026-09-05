-- 04a / business v1. Apply only to the intended database after backup.
-- Existing definitions are checked by verify_migration.py, not silently altered.
CREATE TABLE IF NOT EXISTS ai_asset (
 asset_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 file_name VARCHAR(255) NOT NULL,
 media_type VARCHAR(64) NOT NULL,
 storage_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 size_bytes BIGINT NOT NULL,
 sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 created_at BIGINT NOT NULL,
 expires_at BIGINT NOT NULL,
 UNIQUE KEY uk_ai_asset_storage(storage_key), KEY ix_ai_asset_owner(owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS ai_job (
 request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_json LONGTEXT NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 dispatch_token VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
 checkpoint_json LONGTEXT NULL,
 result_json LONGTEXT NULL,
 error_json TEXT NULL,
 created_at BIGINT NOT NULL,
 updated_at BIGINT NOT NULL,
 UNIQUE KEY uk_ai_job_owner_key(owner_id,idempotency_key),
 KEY ix_ai_job_history(owner_id,created_at,request_id),
 KEY ix_ai_job_pending(state,created_at,request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS ai_capability_binding (
 capability_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 descriptor_json LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS ai_job_event (
 request_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 occurred_at BIGINT NOT NULL,
 PRIMARY KEY(request_id,version),
 CONSTRAINT fk_ai_event_job FOREIGN KEY(request_id) REFERENCES ai_job(request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE IF NOT EXISTS ai_job_capacity (
 id INT PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO ai_job_capacity(id) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM ai_job_capacity WHERE id=1);
