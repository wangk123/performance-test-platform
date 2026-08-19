package com.yr.perftest.platform.evidence.deep;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 深度证据源配置（T11）：`platform.evidence.deep.kinds.<kind>.*`。
 * 默认全部关闭——高影响源（TRACE/PROFILING）接入需显式配置即视为审批。
 */
@ConfigurationProperties(prefix = "platform.evidence.deep")
public class DeepEvidenceProperties {
    private final Map<String, KindConfig> kinds = new HashMap<>();

    public Map<String, KindConfig> getKinds() {
        return kinds;
    }

    public KindConfig forKind(DeepEvidenceKind kind) {
        return kinds.computeIfAbsent(kind.key(), ignored -> new KindConfig());
    }

    public static class KindConfig {
        private boolean enabled = false;
        private String endpoint;
        private int retentionDays = 7;
        private final Map<String, String> promqlTemplates = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public Map<String, String> getPromqlTemplates() {
            return promqlTemplates;
        }
    }
}
