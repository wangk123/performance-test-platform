package com.yr.perftest.platform.analysis;

public final class AnalysisMath {
    private AnalysisMath() {
    }

    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public static Double deltaPct(double first, double second) {
        if (first == 0) {
            return second == 0 ? 0.0 : null;
        }
        return round4((second - first) / first * 100.0);
    }

    public static String direction(Double deltaPct) {
        if (deltaPct == null) {
            return "INCREASING";
        }
        if (deltaPct > 5.0) {
            return "INCREASING";
        }
        if (deltaPct < -5.0) {
            return "DECREASING";
        }
        return "STABLE";
    }
}
