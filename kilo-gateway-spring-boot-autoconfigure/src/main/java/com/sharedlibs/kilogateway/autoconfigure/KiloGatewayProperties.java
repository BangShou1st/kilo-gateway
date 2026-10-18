package com.sharedlibs.kilogateway.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kilo.gateway")
public class KiloGatewayProperties {
    private boolean enabled = true;
    private String baseUrl = "https://ai-gateway.kilo.ai/api/v1";
    private String apiKey = "";
    private String model = "poolside/laguna-s-2.1:free";
    private boolean freeOnly = true;
    private List<String> fallbackModels = new ArrayList<>();
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(90);
    private String proxyHost = "";
    private int proxyPort = 0;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public boolean isFreeOnly() { return freeOnly; }
    public void setFreeOnly(boolean freeOnly) { this.freeOnly = freeOnly; }
    public List<String> getFallbackModels() { return fallbackModels; }
    public void setFallbackModels(List<String> fallbackModels) {
        this.fallbackModels = fallbackModels == null ? new ArrayList<>() : new ArrayList<>(fallbackModels);
    }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
}
