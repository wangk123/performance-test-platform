package com.yr.perftest.platform.envcheck;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "platform.envcheck")
@Component
public class EnvCheckProperties {
    private boolean fixEnabled = true;
    private String secret = "";
    private int probeTimeoutSeconds = 10;

    public boolean isFixEnabled() {
        return fixEnabled;
    }

    public void setFixEnabled(boolean fixEnabled) {
        this.fixEnabled = fixEnabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getProbeTimeoutSeconds() {
        return probeTimeoutSeconds;
    }

    public void setProbeTimeoutSeconds(int probeTimeoutSeconds) {
        this.probeTimeoutSeconds = probeTimeoutSeconds;
    }
}
