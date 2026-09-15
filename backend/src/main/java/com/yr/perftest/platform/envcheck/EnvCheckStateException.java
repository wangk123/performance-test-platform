package com.yr.perftest.platform.envcheck;

/** 用户可触发的环境检查状态拒绝（总闸关闭/重复回滚/凭据缺失等），统一映射 400。 */
public class EnvCheckStateException extends RuntimeException {
    public EnvCheckStateException(String message) {
        super(message);
    }
}
