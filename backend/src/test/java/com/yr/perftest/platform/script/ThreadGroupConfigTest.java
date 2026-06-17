package com.yr.perftest.platform.script;

import com.yr.perftest.platform.TestSupport;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.yr.perftest.platform.TestSupport.*;

public class ThreadGroupConfigTest {

    public static void runAll() {
        defaultValues();
        fromMapWithAllFields();
        fromMapWithMissingFields();
        fromMapWithNullMap();
        fromMapWithInvalidValues();
        fromMapWithScheduler();
        fromMapWithStepping();
        toMapRoundTrip();
        System.out.println("ThreadGroupConfigTest passed");
    }

    static void defaultValues() {
        assertEquals(1, ThreadGroupConfig.DEFAULT.threads(), "default threads");
        assertEquals(0, ThreadGroupConfig.DEFAULT.rampUp(), "default rampUp");
        assertEquals(1, ThreadGroupConfig.DEFAULT.loops(), "default loops");
        assertEquals(0, ThreadGroupConfig.DEFAULT.duration(), "default duration");
        assertFalse(ThreadGroupConfig.DEFAULT.scheduler(), "default scheduler false");
    }

    static void fromMapWithAllFields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("threads", 100);
        map.put("rampUp", 60);
        map.put("loops", 10);
        map.put("duration", 600);
        map.put("scheduler", false);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(map);
        assertEquals(100, config.threads(), "threads from map");
        assertEquals(60, config.rampUp(), "rampUp from map");
        assertEquals(10, config.loops(), "loops from map");
        assertEquals(600, config.duration(), "duration from map");
        assertFalse(config.scheduler(), "scheduler from map");
    }

    static void fromMapWithMissingFields() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("threads", 50);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(map);
        assertEquals(50, config.threads(), "threads present");
        assertEquals(0, config.rampUp(), "rampUp defaults");
        assertEquals(1, config.loops(), "loops defaults");
        assertEquals(0, config.duration(), "duration defaults");
        assertFalse(config.scheduler(), "scheduler defaults false");
    }

    static void fromMapWithNullMap() {
        ThreadGroupConfig config = ThreadGroupConfig.fromMap(null);
        assertEquals(ThreadGroupConfig.DEFAULT, config, "null map returns DEFAULT");
    }

    static void fromMapWithInvalidValues() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("threads", "not-a-number");
        map.put("rampUp", 30);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(map);
        assertEquals(1, config.threads(), "invalid threads falls back to default");
        assertEquals(30, config.rampUp(), "valid rampUp preserved");
    }

    static void fromMapWithScheduler() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("threads", 200);
        map.put("rampUp", 30);
        map.put("loops", -1);
        map.put("duration", 600);
        map.put("scheduler", true);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(map);
        assertEquals(200, config.threads(), "threads for scheduler mode");
        assertEquals(-1, config.loops(), "loops -1 for scheduler mode");
        assertEquals(600, config.duration(), "duration for scheduler mode");
        assertTrue(config.scheduler(), "scheduler true");
    }

    static void fromMapWithStepping() {
        Map<String, Object> stepping = new LinkedHashMap<>();
        stepping.put("initialDelay", 5);
        stepping.put("startUsersCount", 20);
        stepping.put("startUsersPeriod", 30);
        stepping.put("rampUp", 10);
        stepping.put("flightTime", 120);
        stepping.put("stopUsersCount", 10);
        stepping.put("stopUsersPeriod", 15);
        stepping.put("burst", true);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("mode", "stepping");
        map.put("threads", 100);
        map.put("stepping", stepping);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(map);

        assertEquals("stepping", config.mode(), "mode stepping");
        assertEquals(100, config.threads(), "threads for stepping");
        assertEquals(5, config.stepping().initialDelay(), "initial delay");
        assertEquals(20, config.stepping().startUsersCount(), "start users count");
        assertEquals(30, config.stepping().startUsersPeriod(), "start users period");
        assertEquals(10, config.stepping().rampUp(), "stepping rampUp");
        assertEquals(120, config.stepping().flightTime(), "flight time");
        assertEquals(10, config.stepping().stopUsersCount(), "stop users count");
        assertEquals(15, config.stepping().stopUsersPeriod(), "stop users period");
        assertTrue(config.stepping().burst(), "burst true");
        assertEquals("stepping", config.toMap().get("mode"), "exported mode");
        assertTrue(config.toMap().get("stepping") instanceof Map<?, ?>, "exported stepping map");
    }

    static void toMapRoundTrip() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("threads", 200);
        original.put("rampUp", 120);
        original.put("loops", 5);
        original.put("duration", 900);
        original.put("scheduler", false);

        ThreadGroupConfig config = ThreadGroupConfig.fromMap(original);
        Map<String, Object> exported = config.toMap();

        assertEquals(200, exported.get("threads"), "round-trip threads");
        assertEquals(120, exported.get("rampUp"), "round-trip rampUp");
        assertEquals(5, exported.get("loops"), "round-trip loops");
        assertEquals(900, exported.get("duration"), "round-trip duration");
        assertFalse((Boolean) exported.get("scheduler"), "round-trip scheduler false");

        Map<String, Object> schedulerMap = new LinkedHashMap<>();
        schedulerMap.put("threads", 100);
        schedulerMap.put("rampUp", 10);
        schedulerMap.put("loops", -1);
        schedulerMap.put("duration", 300);
        schedulerMap.put("scheduler", true);
        ThreadGroupConfig schedulerConfig = ThreadGroupConfig.fromMap(schedulerMap);
        Map<String, Object> schedulerExported = schedulerConfig.toMap();
        assertTrue((Boolean) schedulerExported.get("scheduler"), "round-trip scheduler true");
    }
}
