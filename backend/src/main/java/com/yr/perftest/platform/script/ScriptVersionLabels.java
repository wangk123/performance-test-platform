package com.yr.perftest.platform.script;

import java.util.regex.Pattern;

/** 语义化版本号（x.x.x）的格式校验、逐段比较与自动递推。 */
public final class ScriptVersionLabels {

    static final String INITIAL = "1.0.0";

    private static final Pattern FORMAT = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private ScriptVersionLabels() {
    }

    public static boolean isValid(String label) {
        return label != null && FORMAT.matcher(label).matches();
    }

    /** 逐段语义化比较：1.0.0 < 1.0.1 < 1.1.0 < 2.0.0。 */
    static int compare(String left, String right) {
        int[] a = segments(left);
        int[] b = segments(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    /** patch 位 +1 的下一版本（快捷执行自动发布用）。 */
    public static String nextPatch(String label) {
        int[] s = segments(label == null || label.isBlank() ? INITIAL : label);
        return s[0] + "." + s[1] + "." + (s[2] + 1);
    }

    private static int[] segments(String label) {
        String[] parts = label.split("\\.");
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }
}
