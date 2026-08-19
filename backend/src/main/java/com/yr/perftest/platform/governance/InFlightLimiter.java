package com.yr.perftest.platform.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内并发（在途请求数）限制（T10）。用于约束同一主体的并发 agent 调用数。
 */
public class InFlightLimiter {
    private final Map<String, AtomicInteger> inFlightByKey = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int maxInFlight) {
        if (key == null || maxInFlight <= 0) {
            return false;
        }
        AtomicInteger counter = inFlightByKey.computeIfAbsent(key, ignored -> new AtomicInteger());
        synchronized (counter) {
            if (counter.get() >= maxInFlight) {
                return false;
            }
            counter.incrementAndGet();
            return true;
        }
    }

    public void release(String key) {
        if (key == null) {
            return;
        }
        AtomicInteger counter = inFlightByKey.get(key);
        if (counter != null) {
            synchronized (counter) {
                if (counter.get() > 0) {
                    counter.decrementAndGet();
                }
                if (counter.get() == 0) {
                    inFlightByKey.remove(key, counter);
                }
            }
        }
    }
}
