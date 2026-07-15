package com.yr.perftest.platform.seed;

@FunctionalInterface
public interface CaptureRowSourceFactory {
    CaptureRowSource open(PersistentSeedDatasourceRecord datasource) throws Exception;
}
