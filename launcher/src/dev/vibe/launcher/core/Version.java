package dev.vibe.launcher.core;

import java.util.ArrayList;
import java.util.List;

/** Numeric version comparison: "2.10.0" is newer than "2.9.3". */
public final class Version {
    private Version() { }

    public static boolean isNewer(String candidate, String current) { return compare(candidate, current) > 0; }

    public static int compare(String left, String right) {
        int[] a = parts(left), b = parts(right);
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int x = index < a.length ? a[index] : 0, y = index < b.length ? b[index] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    private static int[] parts(String value) {
        List<Integer> result = new ArrayList<Integer>();
        if (value != null) {
            for (String piece : value.replaceFirst("^[^0-9]*", "").split("[^0-9]+")) {
                if (piece.isEmpty()) continue;
                try { result.add(Integer.parseInt(piece)); } catch (NumberFormatException ignored) { result.add(Integer.MAX_VALUE); }
            }
        }
        int[] array = new int[result.size()];
        for (int index = 0; index < array.length; index++) array[index] = result.get(index);
        return array;
    }
}
