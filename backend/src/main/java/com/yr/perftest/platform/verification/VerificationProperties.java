package com.yr.perftest.platform.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 优化验证护栏配置（T9）：可通过 `platform.verification.*` 覆盖。
 */
@ConfigurationProperties(prefix = "platform.verification")
public class VerificationProperties {
    private double p95RegressionPct = 5.0;
    private double significantPct = 5.0;
    private double errorRateIncreasePct = 0.01;

    public double getP95RegressionPct() {
        return p95RegressionPct;
    }

    public void setP95RegressionPct(double p95RegressionPct) {
        this.p95RegressionPct = p95RegressionPct;
    }

    public double getSignificantPct() {
        return significantPct;
    }

    public void setSignificantPct(double significantPct) {
        this.significantPct = significantPct;
    }

    public double getErrorRateIncreasePct() {
        return errorRateIncreasePct;
    }

    public void setErrorRateIncreasePct(double errorRateIncreasePct) {
        this.errorRateIncreasePct = errorRateIncreasePct;
    }
}
