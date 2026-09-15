package com.yr.perftest.platform.envcheck;

import java.util.List;

/** 开跑校验失败：目标机缺少凭据，携带缺失凭据的机器地址清单。 */
public class EnvCheckCredentialMissingException extends RuntimeException {

    private final List<String> missingHosts;

    public EnvCheckCredentialMissingException(String message, List<String> missingHosts) {
        super(message);
        this.missingHosts = missingHosts;
    }

    public List<String> getMissingHosts() {
        return missingHosts;
    }

    public List<String> missingHosts() {
        return missingHosts;
    }
}
