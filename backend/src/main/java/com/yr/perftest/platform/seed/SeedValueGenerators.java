package com.yr.perftest.platform.seed;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class SeedValueGenerators {
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    private SeedValueGenerators() {
    }

    public static String generate(String generatorKey) {
        if (generatorKey == null || generatorKey.isBlank()) {
            throw new SeedValidationException("generator is required");
        }
        return switch (generatorKey) {
            case "randomMobile" -> randomMobile();
            case "randomIdCard" -> randomIdCard();
            case "seq", "sequence" -> String.valueOf(SEQ.incrementAndGet());
            case "uuid" -> java.util.UUID.randomUUID().toString().replace("-", "");
            default -> throw new SeedValidationException("unknown generator: " + generatorKey);
        };
    }

    public static String randomMobile() {
        String[] prefixes = {"130", "131", "132", "133", "135", "136", "137", "138", "139", "150", "151", "152", "158", "186", "188"};
        String prefix = prefixes[ThreadLocalRandom.current().nextInt(prefixes.length)];
        return prefix + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    public static String randomIdCard() {
        String area = "110101";
        int year = 1980 + ThreadLocalRandom.current().nextInt(30);
        int month = 1 + ThreadLocalRandom.current().nextInt(12);
        int day = 1 + ThreadLocalRandom.current().nextInt(28);
        String body = area + String.format("%04d%02d%02d", year, month, day)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        return body + idCardChecksum(body);
    }

    private static char idCardChecksum(String body17) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body17.charAt(i) - '0') * weights[i];
        }
        return checks[sum % 11];
    }
}
