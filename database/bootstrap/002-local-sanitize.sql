-- Remove historical credentials, author-machine endpoints and stale runtime
-- bindings while preserving users, permissions, menus, dictionaries and schema.
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `sys_log`;
TRUNCATE TABLE `sys_data_log`;
TRUNCATE TABLE `tab_ai_history`;
TRUNCATE TABLE `tab_ai_model_bund`;

UPDATE `tab_ai_model`
SET `ai_weights` = NULL,
    `ai_config` = NULL,
    `ai_name_name` = NULL;

UPDATE `tab_ai_subscription`
SET `event_url` = NULL,
    `remake` = NULL,
    `push_static` = 0;

UPDATE `tab_maxkb_model`
SET `status` = NULL,
    `api_key` = NULL,
    `api_url` = NULL,
    `api_js` = NULL,
    `start_url` = NULL;

UPDATE `jimu_report_data_source`
SET `db_url` = NULL,
    `db_username` = NULL,
    `db_password` = NULL,
    `connect_times` = 0;

UPDATE `jimu_report_db`
SET `api_url` = NULL
WHERE `api_url` IS NOT NULL AND `api_url` <> '';

SET FOREIGN_KEY_CHECKS = 1;
