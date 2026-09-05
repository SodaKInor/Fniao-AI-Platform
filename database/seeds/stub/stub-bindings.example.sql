-- Development stub seed only. Replace __OWNER_ID__ in an isolated database copy.
-- Every enabled entry is explicitly simulated and uses providerKey=stub.
INSERT INTO ai_capability_binding(capability_code,descriptor_json) VALUES
('image-detection.v1','{"snapshot":{"capabilityCode":"image-detection.v1","capabilityVersion":"stub-v1","providerKey":"stub","adapterId":"sync-draft-v0.1","providerCapabilityCode":"image-detection.v1","providerVersion":"stub-simulated-v1","features":{"query":false,"cancel":false,"deduplication":false}},"displayName":"模拟图片检测（HTTP stub）","enabled":true,"available":true,"simulated":true,"unavailableReason":"","inputMediaTypes":["image/png","image/jpeg"],"maxInputBytes":10485760,"maxOutputBytes":10485760,"maxWaitMillis":1500}'),
('video-file-analysis.v1','{"snapshot":{"capabilityCode":"video-file-analysis.v1","capabilityVersion":"stub-v1","providerKey":"stub","adapterId":"video-draft-v0.2","providerCapabilityCode":"video-file-analysis.v1","providerVersion":"stub-simulated-v1","features":{"query":false,"cancel":false,"deduplication":false}},"displayName":"模拟上传视频分析（HTTP stub）","enabled":true,"available":true,"simulated":true,"unavailableReason":"","inputMediaTypes":["video/mp4"],"maxInputBytes":33554432,"maxOutputBytes":33554432,"maxWaitMillis":1500}'),
('video-stream-analysis.v1','{"snapshot":{"capabilityCode":"video-stream-analysis.v1","capabilityVersion":"stub-v1","providerKey":"stub","adapterId":"stream-draft-v0.2","providerCapabilityCode":"video-stream-analysis.v1","providerVersion":"stub-simulated-v1","features":{"query":true,"cancel":true,"deduplication":false}},"displayName":"模拟实时事件（HTTP stub）","enabled":true,"available":true,"simulated":true,"unavailableReason":"","inputMediaTypes":[],"maxInputBytes":1,"maxOutputBytes":10485760,"maxWaitMillis":1500}')
ON DUPLICATE KEY UPDATE descriptor_json=VALUES(descriptor_json);

INSERT INTO ai_stream_source(stream_source_id,owner_id,display_name,provider_source_ref,enabled,
 unavailable_reason,created_at,updated_at)
VALUES('stub-source-01','__OWNER_ID__','合成演示来源','synthetic-camera-01',1,NULL,
 UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000,UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000)
ON DUPLICATE KEY UPDATE owner_id=VALUES(owner_id),display_name=VALUES(display_name),
 provider_source_ref=VALUES(provider_source_ref),enabled=VALUES(enabled),unavailable_reason=NULL,updated_at=VALUES(updated_at);
