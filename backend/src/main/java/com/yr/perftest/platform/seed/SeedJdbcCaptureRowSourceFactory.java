package com.yr.perftest.platform.seed;

public final class SeedJdbcCaptureRowSourceFactory implements CaptureRowSourceFactory {
    private final SeedCredentialCipher cipher;

    public SeedJdbcCaptureRowSourceFactory(SeedCredentialCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public CaptureRowSource open(PersistentSeedDatasourceRecord datasource) throws Exception {
        return new SeedJdbcCaptureRowSource(SeedJdbcSupport.open(datasource, cipher));
    }
}
