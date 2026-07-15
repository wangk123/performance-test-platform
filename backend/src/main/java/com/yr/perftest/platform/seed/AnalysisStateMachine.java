package com.yr.perftest.platform.seed;

import java.util.Set;

public final class AnalysisStateMachine {
    private static final Set<String> ACTIVE = Set.of(
            "QUEUED",
            "VALIDATING",
            "DIFFING",
            "INFERRING",
            "PERSISTING",
            "CANCEL_REQUESTED"
    );

    private static final Set<String> TERMINAL = Set.of(
            "SUCCEEDED",
            "FAILED",
            "CANCELED",
            "INTERRUPTED",
            "DELETING"
    );

    private AnalysisStateMachine() {
    }

    public static boolean isActive(String status) {
        return ACTIVE.contains(status);
    }

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(status);
    }

    public static void requireTransition(String current, String target) {
        if (current.equals(target) || allowed(current, target)) {
            return;
        }
        throw new IllegalStateException(
                "invalid capture analysis transition " + current + " -> " + target
        );
    }

    private static boolean allowed(String current, String target) {
        return switch (current) {
            case "QUEUED" -> target.equals("VALIDATING")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "VALIDATING" -> target.equals("DIFFING")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "DIFFING" -> target.equals("INFERRING")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "INFERRING" -> target.equals("PERSISTING")
                    || target.equals("CANCEL_REQUESTED")
                    || target.equals("FAILED")
                    || target.equals("INTERRUPTED");
            case "PERSISTING" -> target.equals("SUCCEEDED")
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
