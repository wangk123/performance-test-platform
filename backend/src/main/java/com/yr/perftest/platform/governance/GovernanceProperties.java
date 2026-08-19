package com.yr.perftest.platform.governance;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 治理配置（T10）：限流窗口、容量与开关，全部可通过 `platform.governance.*` 覆盖。
 */
@ConfigurationProperties(prefix = "platform.governance")
public class GovernanceProperties {
    private boolean enabled = true;
    private final RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class RateLimit {
        private long windowMillis = 10_000;
        private int humanCapacity = 120;
        private int machineCapacity = 600;
        private int anonymousCapacity = 30;
        private int maxInFlightHuman = 10;
        private int maxInFlightMachine = 50;
        private int maxInFlightAnonymous = 5;

        public long getWindowMillis() {
            return windowMillis;
        }

        public void setWindowMillis(long windowMillis) {
            this.windowMillis = windowMillis;
        }

        public int getHumanCapacity() {
            return humanCapacity;
        }

        public void setHumanCapacity(int humanCapacity) {
            this.humanCapacity = humanCapacity;
        }

        public int getMachineCapacity() {
            return machineCapacity;
        }

        public void setMachineCapacity(int machineCapacity) {
            this.machineCapacity = machineCapacity;
        }

        public int getAnonymousCapacity() {
            return anonymousCapacity;
        }

        public void setAnonymousCapacity(int anonymousCapacity) {
            this.anonymousCapacity = anonymousCapacity;
        }

        public int getMaxInFlightHuman() {
            return maxInFlightHuman;
        }

        public void setMaxInFlightHuman(int maxInFlightHuman) {
            this.maxInFlightHuman = maxInFlightHuman;
        }

        public int getMaxInFlightMachine() {
            return maxInFlightMachine;
        }

        public void setMaxInFlightMachine(int maxInFlightMachine) {
            this.maxInFlightMachine = maxInFlightMachine;
        }

        public int getMaxInFlightAnonymous() {
            return maxInFlightAnonymous;
        }

        public void setMaxInFlightAnonymous(int maxInFlightAnonymous) {
            this.maxInFlightAnonymous = maxInFlightAnonymous;
        }
    }
}
