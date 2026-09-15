package com.yr.perftest.platform.envcheck;

/** LOCAL 型检查项：在平台本机执行检查（spec §3.2）。 */
public interface LocalCheckItem extends EnvCheckItem {

    LocalVerdict check(LocalCheckContext ctx);
}
