package org.jeecg.modules.ai.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secrets remain file references. Do not generate a value-dumping toString. */
@ConfigurationProperties(prefix = "wgai.inference")
public class ProviderProperties {
    private String mode = "disabled";
    private boolean developmentStub;
    private String baseUrl = "";
    private String approvedOrigin = "";
    private String apiPath = "/infer";
    private String videoApiPath = "/video-jobs";
    private String streamSourcesPath = "/stream-sources";
    private String streamSessionsPath = "/stream-sessions";
    private String providerKey = "remote";
    private String tokenFile = "";
    private String caFile = "";
    private int connectTimeoutMs = 3000;
    private int requestTimeoutMs = 120000;
    private int transferTimeoutMs = 30000;
    private int maxInflight = 1;
    private long uploadMaxBytes = 10485760;
    private long outputMaxBytes = 10485760;
    private long videoUploadMaxBytes = 536870912;
    private long videoOutputMaxBytes = 536870912;

    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public boolean isDevelopmentStub() { return developmentStub; }
    public void setDevelopmentStub(boolean value) { developmentStub = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getApprovedOrigin() { return approvedOrigin; }
    public void setApprovedOrigin(String value) { approvedOrigin = value; }
    public String getApiPath() { return apiPath; }
    public void setApiPath(String value) { apiPath = value; }
    public String getVideoApiPath() { return videoApiPath; }
    public void setVideoApiPath(String value) { videoApiPath = value; }
    public String getStreamSourcesPath() { return streamSourcesPath; }
    public void setStreamSourcesPath(String value) { streamSourcesPath = value; }
    public String getStreamSessionsPath() { return streamSessionsPath; }
    public void setStreamSessionsPath(String value) { streamSessionsPath = value; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String value) { providerKey = value; }
    public String getTokenFile() { return tokenFile; }
    public void setTokenFile(String value) { tokenFile = value; }
    public String getCaFile() { return caFile; }
    public void setCaFile(String value) { caFile = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int value) { requestTimeoutMs = value; }
    public int getTransferTimeoutMs() { return transferTimeoutMs; }
    public void setTransferTimeoutMs(int value) { transferTimeoutMs = value; }
    public int getMaxInflight() { return maxInflight; }
    public void setMaxInflight(int value) { maxInflight = value; }
    public long getUploadMaxBytes() { return uploadMaxBytes; }
    public void setUploadMaxBytes(long value) { uploadMaxBytes = value; }
    public long getOutputMaxBytes() { return outputMaxBytes; }
    public void setOutputMaxBytes(long value) { outputMaxBytes = value; }
    public long getVideoUploadMaxBytes() { return videoUploadMaxBytes; }
    public void setVideoUploadMaxBytes(long value) { videoUploadMaxBytes = value; }
    public long getVideoOutputMaxBytes() { return videoOutputMaxBytes; }
    public void setVideoOutputMaxBytes(long value) { videoOutputMaxBytes = value; }

    public boolean validLimits() {
        return connectTimeoutMs > 0 && requestTimeoutMs > 0 && transferTimeoutMs > 0
                && maxInflight > 0 && maxInflight <= 100 && uploadMaxBytes > 0 && outputMaxBytes > 0
                && uploadMaxBytes <= 10485760 && outputMaxBytes <= 10485760
                && videoUploadMaxBytes > 0 && videoUploadMaxBytes <= 2147483648L
                && videoOutputMaxBytes > 0 && videoOutputMaxBytes <= 2147483648L;
    }
}
