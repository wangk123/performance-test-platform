package com.yr.perftest.platform.task;

/**
 * 触发执行时的内联线程组参数覆盖：在 preset 合并结果基础上按字段覆盖，null 表示不覆盖。
 */
public record ThreadGroupOverrides(Integer threads, Integer rampUpSec, Integer durationSec) {
    public boolean isEmpty() {
        return threads == null && rampUpSec == null && durationSec == null;
    }
}
