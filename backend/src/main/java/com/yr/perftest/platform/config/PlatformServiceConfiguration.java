package com.yr.perftest.platform.config;

import com.yr.perftest.platform.identity.PersistentUserAccountRecord;
import com.yr.perftest.platform.identity.PersistentUserAccountRepository;
import com.yr.perftest.platform.identity.SystemRole;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformServiceConfiguration {
    @Bean
    public ApplicationRunner demoUserSeeder(PersistentUserAccountRepository userAccountRepository) {
        return args -> {
            if (!userAccountRepository.existsById("admin")) {
                userAccountRepository.save(new PersistentUserAccountRecord(
                        "admin",
                        "admin123",
                        "平台管理员",
                        true,
                        SystemRole.ADMIN
                ));
            }
            if (!userAccountRepository.existsById("tester")) {
                userAccountRepository.save(new PersistentUserAccountRecord(
                        "tester",
                        "tester123",
                        "性能测试工程师",
                        true,
                        SystemRole.PROJECT_MEMBER
                ));
            }
        };
    }
}
