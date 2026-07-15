package com.yr.perftest.platform.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class SeedConfiguration {
    @Bean
    public SeedCredentialCipher seedCredentialCipher(
            @Value("${platform.seed.credential-secret:perftest-seed-default-key!}") String secret
    ) {
        return new SeedCredentialCipher(secret);
    }

    @Bean
    public CaptureChunkStore captureChunkStore(
            @Value("${platform.storage.root:./storage}") String storageRoot
    ) {
        return new CaptureChunkStore(Path.of(storageRoot));
    }

    @Bean
    public DiskLowWaterGuard captureDiskLowWaterGuard(
            @Value("${platform.storage.root:./storage}") String storageRoot,
            @Value("${platform.seed.disk-low-water-bytes:1073741824}") long lowWaterBytes
    ) {
        return new DiskLowWaterGuard(Path.of(storageRoot), lowWaterBytes);
    }

    @Bean
    public CapturePartitionPlanner capturePartitionPlanner() {
        return new CapturePartitionPlanner();
    }

    @Bean
    public CaptureRowSourceFactory captureRowSourceFactory(SeedCredentialCipher cipher) {
        return new SeedJdbcCaptureRowSourceFactory(cipher);
    }
}
