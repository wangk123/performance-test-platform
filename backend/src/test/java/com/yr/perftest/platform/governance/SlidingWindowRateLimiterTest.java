package com.yr.perftest.platform.governance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000);

    @Test
    void rejectsRequestsBeyondCapacityWithinWindow() {
        assertThat(limiter.tryAcquire("k1", 100L, 2)).isTrue();
        assertThat(limiter.tryAcquire("k1", 200L, 2)).isTrue();
        assertThat(limiter.tryAcquire("k1", 300L, 2)).isFalse();
    }

    @Test
    void acceptsAgainAfterWindowSlidesPast() {
        assertThat(limiter.tryAcquire("k1", 100L, 1)).isTrue();
        assertThat(limiter.tryAcquire("k1", 200L, 1)).isFalse();
        assertThat(limiter.tryAcquire("k1", 1101L, 1)).isTrue();
    }

    @Test
    void keysAreIndependent() {
        assertThat(limiter.tryAcquire("k1", 100L, 1)).isTrue();
        assertThat(limiter.tryAcquire("k2", 100L, 1)).isTrue();
    }

    @Test
    void zeroOrNegativeCapacityAlwaysRejects() {
        assertThat(limiter.tryAcquire("k1", 100L, 0)).isFalse();
        assertThat(limiter.tryAcquire(null, 100L, 1)).isFalse();
    }
}
