package com.yr.perftest.platform.config;

import com.yr.perftest.platform.identity.AuthenticationService;
import com.yr.perftest.platform.identity.SystemRole;
import com.yr.perftest.platform.project.ProjectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformServiceConfiguration {
    @Bean
    public AuthenticationService authenticationService() {
        AuthenticationService service = new AuthenticationService();
        service.register("admin", "admin123", "平台管理员", true, SystemRole.ADMIN);
        service.register("tester", "tester123", "性能测试工程师", true, SystemRole.PROJECT_MEMBER);
        return service;
    }

    @Bean
    public ProjectService projectService() {
        return new ProjectService();
    }
}
