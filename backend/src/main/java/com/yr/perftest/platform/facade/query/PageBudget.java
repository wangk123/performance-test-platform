package com.yr.perftest.platform.facade.query;

public record PageBudget(int maxItems, long maxBytes, long maxMillis) {
    public static PageBudget defaults() {
        return new PageBudget(1000, 1_048_576, 3000);
    }

    public void validate() {
        if (maxItems <= 0 || maxBytes <= 0 || maxMillis <= 0) {
            throw new IllegalArgumentException("Page budget values must be positive");
        }
    }
}
