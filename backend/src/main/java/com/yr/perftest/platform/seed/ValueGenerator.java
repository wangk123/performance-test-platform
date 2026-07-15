package com.yr.perftest.platform.seed;

@FunctionalInterface
public interface ValueGenerator {
    String generate(String mapKey);
}
