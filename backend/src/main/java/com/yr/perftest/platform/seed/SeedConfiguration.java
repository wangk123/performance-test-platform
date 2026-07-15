package com.yr.perftest.platform.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedConfiguration {
    @Bean
    public SeedCredentialCipher seedCredentialCipher(
            @Value("${platform.seed.credential-secret:perftest-seed-default-key!}") String secret
    ) {
        return new SeedCredentialCipher(secret);
    }
}
