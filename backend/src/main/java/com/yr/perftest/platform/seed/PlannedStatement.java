package com.yr.perftest.platform.seed;

import java.util.Map;

public record PlannedStatement(String type, String table, Map<String, String> values, Map<String, String> where) {
}
