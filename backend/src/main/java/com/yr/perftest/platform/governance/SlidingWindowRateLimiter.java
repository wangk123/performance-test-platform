package com.yr.perftest.platform.governance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口速率限制（T10）。窗口按调用方传入的毫秒时间推进，便于测试确定性。
 */
public class SlidingWindowRateLimiter {
    private static final int CLEANUP_THRESHOLD = 2000;

    private final long windowMillis;
    private final Map<String, Deque<Long>> hitsByKey = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public boolean tryAcquire(String key, long nowMillis, int capacity) {
        if (key == null || capacity <= 0) {
            return false;
        }
        Deque<Long> deque = hitsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            long floor = nowMillis - windowMillis;
            while (!deque.isEmpty() && deque.peekFirst() <= floor) {
                deque.pollFirst();
            }
            if (deque.size() >= capacity) {
                return false;
            }
            deque.addLast(nowMillis);
            cleanupIfLarge(nowMillis);
            return true;
        }
    }

    private void cleanupIfLarge(long nowMillis) {
        if (hitsByKey.size() <= CLEANUP_THRESHOLD) {
            return;
        }
        hitsByKey.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                long floor = nowMillis - windowMillis;
                while (!deque.isEmpty() && deque.peekFirst() <= floor) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }
}
