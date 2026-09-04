package org.jeecg.modules.ai.config.provider;

import org.jeecg.modules.ai.client.ProviderRequestChecks;
import org.jeecg.modules.ai.domain.Capability;
import org.jeecg.modules.ai.client.draft.DraftEndpoint;

/** Local configuration evaluation; it never makes network calls or changes core health. */
public final class ProviderAvailability {
    private final ProviderProperties properties;
    private final org.jeecg.modules.ai.client.ProviderObservations observations;

    public ProviderAvailability(ProviderProperties properties, org.jeecg.modules.ai.client.ProviderObservations observations) {
        this.properties = properties; this.observations = observations;
    }

    public String modeReason() {
        if ("disabled".equals(properties.getMode())) return "AI 调用已停用";
        if (!properties.validLimits()) return "AI 限额配置无效";
        if ("mock".equals(properties.getMode())) return "";
        if (!"remote".equals(properties.getMode())) return "AI 模式配置无效";
        try {
            new DraftEndpoint(properties.getBaseUrl(), properties.getApprovedOrigin(), properties.getApiPath(),
                    properties.getVideoApiPath(), properties.getStreamSourcesPath(),
                    properties.getStreamSessionsPath(), false);
            ProviderCredentials.read(properties.getTokenFile());
            ProviderTrust.configure(new okhttp3.OkHttpClient.Builder(), properties.getCaFile());
        } catch (RuntimeException error) { return "外部服务地址、凭据或信任配置不完整"; }
        String observed = observations.reason(properties.getProviderKey());
        return "真实服务协议尚未确认；" + (observed.isEmpty() ? "外部可达性曾确认" : observed) + "；模型就绪状态未确认";
    }

    public String reason(Capability capability) {
        String reason = modeReason();
        if (!reason.isEmpty()) return reason;
        if (!"image-detection.v1".equals(capability.getSnapshot().getCapabilityCode())
                || !capability.isSimulated()
                || !ProviderRequestChecks.binding(capability.getSnapshot(), "mock", "mock-v1")) {
            return "能力绑定不受当前模拟适配器支持";
        }
        return "";
    }

    public String videoReason() {
        String common = modeReason();
        if (!common.isEmpty()) return common;
        return "当前模拟适配器不支持上传视频";
    }

    public String streamStartReason() {
        String common = modeReason();
        if (!common.isEmpty()) return common;
        return "当前模拟适配器不支持实时流";
    }

    public String streamSessionQueryReason() { return "真实流会话查询协议尚未确认"; }

    public String streamEventQueryReason() { return "真实流事件查询协议尚未确认"; }

    public String streamStopReason() { return "真实流停止协议尚未确认"; }
}
