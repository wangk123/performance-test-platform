package com.yr.perftest.platform.script;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly-typed representation of a JMeter ThreadGroup's configuration.
 * Replaces the untyped {@code Map<String, Object>} for THREAD_GROUP steps.
 */
public record ThreadGroupConfig(
        int threads,
        int rampUp,
        int loops,
        int duration,
        boolean scheduler,
        String mode,
        SteppingConfig stepping
) {
    public static final String MODE_COUNT = "count";
    public static final String MODE_DURATION = "duration";
    public static final String MODE_STEPPING = "stepping";
    public static final ThreadGroupConfig DEFAULT = new ThreadGroupConfig(1, 0, 1, 0, false, MODE_COUNT, SteppingConfig.DEFAULT);

    public ThreadGroupConfig(int threads, int rampUp, int loops, int duration, boolean scheduler) {
        this(threads, rampUp, loops, duration, scheduler, scheduler ? MODE_DURATION : MODE_COUNT, SteppingConfig.DEFAULT);
    }

    public ThreadGroupConfig {
        mode = normalizeMode(mode, scheduler);
        stepping = stepping == null ? SteppingConfig.DEFAULT : stepping;
    }

    public static ThreadGroupConfig fromMap(Map<String, Object> map) {
        if (map == null) {
            return DEFAULT;
        }
        boolean scheduler = boolFromMap(map, "scheduler", DEFAULT.scheduler);
        String mode = normalizeMode(stringFromMap(map, "mode", ""), scheduler);
        return new ThreadGroupConfig(
                intFromMap(map, "threads", DEFAULT.threads),
                intFromMap(map, "rampUp", DEFAULT.rampUp),
                intFromMap(map, "loops", DEFAULT.loops),
                intFromMap(map, "duration", DEFAULT.duration),
                scheduler,
                mode,
                SteppingConfig.fromMap(map.get("stepping"))
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("threads", threads);
        map.put("rampUp", rampUp);
        map.put("loops", loops);
        map.put("duration", duration);
        map.put("scheduler", scheduler);
        map.put("mode", mode);
        map.put("stepping", stepping.toMap());
        return map;
    }

    private static String normalizeMode(String value, boolean scheduler) {
        if (MODE_STEPPING.equals(value)) {
            return MODE_STEPPING;
        }
        if (MODE_DURATION.equals(value) || scheduler) {
            return MODE_DURATION;
        }
        return MODE_COUNT;
    }

    private static String stringFromMap(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static int intFromMap(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean boolFromMap(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public record SteppingConfig(
            int initialDelay,
            int startUsersCount,
            int startUsersPeriod,
            int rampUp,
            int flightTime,
            int stopUsersCount,
            int stopUsersPeriod,
            boolean burst
    ) {
        public static final SteppingConfig DEFAULT = new SteppingConfig(0, 10, 30, 0, 60, 10, 30, false);

        public static SteppingConfig fromMap(Object source) {
            if (!(source instanceof Map<?, ?> rawMap)) {
                return DEFAULT;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> map.put(String.valueOf(key), value));
            return new SteppingConfig(
                    intFromMap(map, "initialDelay", DEFAULT.initialDelay),
                    intFromMap(map, "startUsersCount", DEFAULT.startUsersCount),
                    intFromMap(map, "startUsersPeriod", DEFAULT.startUsersPeriod),
                    intFromMap(map, "rampUp", DEFAULT.rampUp),
                    intFromMap(map, "flightTime", DEFAULT.flightTime),
                    intFromMap(map, "stopUsersCount", DEFAULT.stopUsersCount),
                    intFromMap(map, "stopUsersPeriod", DEFAULT.stopUsersPeriod),
                    boolFromMap(map, "burst", DEFAULT.burst)
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("initialDelay", initialDelay);
            map.put("startUsersCount", startUsersCount);
            map.put("startUsersPeriod", startUsersPeriod);
            map.put("rampUp", rampUp);
            map.put("flightTime", flightTime);
            map.put("stopUsersCount", stopUsersCount);
            map.put("stopUsersPeriod", stopUsersPeriod);
            map.put("burst", burst);
            return map;
        }
    }
}
