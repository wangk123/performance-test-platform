package com.yr.perftest.platform.verification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 取证/验证装配（T9）。
 */
@Configuration
@EnableConfigurationProperties(VerificationProperties.class)
public class VerificationConfiguration {
}
