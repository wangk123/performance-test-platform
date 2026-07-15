package com.yr.perftest.platform.seed;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface CaptureBatchConsumer {
    boolean accept(List<Map<String, Object>> rows) throws Exception;
}
