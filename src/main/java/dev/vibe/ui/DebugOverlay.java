package dev.vibe.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Actual Forge F3 text rectangles in scaled GUI coordinates, scoped to a HUD pass. */
public final class DebugOverlay {
    private static final List<int[]> TEXT = new ArrayList<int[]>();

    private DebugOverlay() { }

    public static void begin(int screenWidth, int fontHeight, List<String> left,
                             List<String> right, ToIntFunction<String> measure) {
        clear();
        addLines(screenWidth, fontHeight, left, false, measure);
        addLines(screenWidth, fontHeight, right, true, measure);
    }

    private static void addLines(int screenWidth, int fontHeight, List<String> lines,
                                 boolean rightAligned, ToIntFunction<String> measure) {
        int y = 2;
        for (String line : lines) {
            // Forge skips null entries without advancing; empty strings occupy a row.
            if (line == null) continue;
            int width = measure.applyAsInt(line);
            if (width > 0) {
                int x = rightAligned ? screenWidth - 2 - width : 2;
                TEXT.add(new int[] {x - 1, y - 1, x + width + 1, y + fontHeight - 1});
            }
            y += fontHeight;
        }
    }

    public static boolean overlaps(float left, float top, float right, float bottom) {
        if (right <= left || bottom <= top) return false;
        for (int[] text : TEXT) {
            if (left < text[2] && right > text[0] && top < text[3] && bottom > text[1]) return true;
        }
        return false;
    }

    public static boolean isActive() {
        return !TEXT.isEmpty();
    }

    public static void clear() {
        TEXT.clear();
    }
}
