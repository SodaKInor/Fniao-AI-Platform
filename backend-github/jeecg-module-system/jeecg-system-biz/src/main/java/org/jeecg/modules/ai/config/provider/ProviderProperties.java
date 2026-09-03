package org.jeecg.modules.ai.config.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secrets remain file references. Do not generate a value-dumping toString. */
@ConfigurationProperties(prefix = "wgai.inference")
public class ProviderProperties {
    private String mode = "disabled";
    private String baseUrl = "";
    private String approvedOrigin = "";
    private String apiPath = "/infer";
    private String providerKey = "remote";
    private String tokenFile = "";
    private String caFile = "";
    private int connectTimeoutMs = 3000;
    private int requestTimeoutMs = 120000;
    private int transferTimeoutMs = 30000;
    private int maxInflight = 1;
    private long uploadMaxBytes = 10485760;
    private long outputMaxBytes = 10485760;

    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getApprovedOrigin() { return approvedOrigin; }
    public void setApprovedOrigin(String value) { approvedOrigin = value; }
    public String getApiPath() { return apiPath; }
    public void setApiPath(String value) { apiPath = value; }
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

    public boolean validLimits() {
        return connectTimeoutMs > 0 && requestTimeoutMs > 0 && transferTimeoutMs > 0
                && maxInflight > 0 && maxInflight <= 100 && uploadMaxBytes > 0 && outputMaxBytes > 0
                && uploadMaxBytes <= 10485760 && outputMaxBytes <= 10485760;
    }
}
