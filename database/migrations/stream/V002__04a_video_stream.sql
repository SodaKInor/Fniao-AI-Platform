-- 04a / business v1.1. Additive only: preserve V001 tables and all historical rows.
CREATE TABLE IF NOT EXISTS ai_stream_source (
 stream_source_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 display_name VARCHAR(160) NOT NULL,
 provider_source_ref VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 0,
 unavailable_reason VARCHAR(255) NULL,
 created_at BIGINT NOT NULL,
 updated_at BIGINT NOT NULL,
 KEY ix_ai_stream_source_owner(owner_id,enabled,display_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ai_stream_session (
 session_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
 idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_json LONGTEXT NOT NULL,
 stream_source_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 dispatch_token VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
 provider_session_id VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
 provider_cursor VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL,
 unknown_reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 error_json TEXT NULL,
 created_at BIGINT NOT NULL,
 updated_at BIGINT NOT NULL,
 UNIQUE KEY uk_ai_stream_session_owner_key(owner_id,idempotency_key),
 KEY ix_ai_stream_session_pending(state,created_at,session_id),
 KEY ix_ai_stream_session_owner(owner_id,created_at,session_id),
 CONSTRAINT fk_ai_stream_session_source FOREIGN KEY(stream_source_id) REFERENCES ai_stream_source(stream_source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS ai_stream_event (
 session_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 provider_event_id VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 event_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 offset_millis BIGINT NOT NULL,
 occurred_at BIGINT NOT NULL,
 event_type VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 score DECIMAL(20,10) NULL,
 snapshot_asset_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
 PRIMARY KEY(session_id,provider_event_id),
 UNIQUE KEY uk_ai_stream_event_id(event_id),
 KEY ix_ai_stream_event_order(session_id,offset_millis,event_id),
 CONSTRAINT fk_ai_stream_event_session FOREIGN KEY(session_id) REFERENCES ai_stream_session(session_id),
 CONSTRAINT fk_ai_stream_event_asset FOREIGN KEY(snapshot_asset_id) REFERENCES ai_asset(asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
