package com.yr.perftest.platform.seed;

import java.util.Set;

public final class CaptureSampleStateMachine {
    private static final Set<String> ACTIVE = Set.of(
            "QUEUED",
            "PREPARING",
            "CAPTURING",
            "CANCEL_REQUESTED"
    );

    private CaptureSampleStateMachine() {
    }

    public static boolean isActive(String status) {
        return ACTIVE.contains(status);
    }

    public static boolean isTerminal(String status) {
        return Set.of("SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED", "DELETING")
                .contains(status);
    }

    public static void requireTransition(String current, String target) {
        if (current.equals(target) || allowed(current, target)) {
            return;
        }
        throw new IllegalStateException("invalid capture sample transition "
                + current + " -> " + target);
    }

    private static boolean allowed(String current, String target) {
        return switch (current) {
            case "QUEUED" -> target.equals("PREPARING")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "PREPARING" -> target.equals("CAPTURING")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "CAPTURING" -> target.equals("SUCCEEDED")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "CANCEL_REQUESTED" -> target.equals("CANCELED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "SUCCEEDED", "FAILED", "CANCELED", "INTERRUPTED", "DELETING" ->
                    target.equals("DELETING");
            default -> false;
        };
    }
}
