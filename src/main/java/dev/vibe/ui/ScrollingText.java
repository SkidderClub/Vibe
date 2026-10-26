package dev.vibe.ui;

/** Clipped, elapsed-time text motion with a one-second pause at either end. */
public final class ScrollingText {
    public enum Alignment { LEFT, CENTER, RIGHT }
    private ScrollingText() { }

    public static float offset(float textWidth, float available, long millis) {
        float overflow = Math.max(0, textWidth - available);
        if (overflow <= 0) return 0;
        double travel = overflow / 24.0;
        double phase = Math.floorMod(millis, 86400000L) / 1000.0 % (travel * 2 + 2);
        return (float) (phase < 1 ? 0 : phase < travel + 1 ? (phase - 1) * 24
                : phase < travel + 2 ? overflow : overflow - (phase - travel - 2) * 24);
    }

    public static void draw(NeverLoseFont font, String text, float left, float y, float available,
                            int color, Alignment alignment) {
        draw(font, text, left, y, available, color, alignment, System.currentTimeMillis());
    }
    public static void draw(NeverLoseFont font, String text, float left, float y, float available,
                            int color, Alignment alignment, long millis) {
        if (available <= 0 || text.isEmpty()) return;
        float width = font.width(text);
        if (width <= available) {
            float spare = available - width;
            font.draw(text, left + (alignment == Alignment.RIGHT ? spare : alignment == Alignment.CENTER ? spare / 2 : 0), y, color);
            return;
        }
        float height = Math.max(16, font.inkTop(text) + font.inkHeight(text) + 2);
        try (GuiClip clip = GuiClip.local(left, y - 1, available, height)) {
            font.draw(text, left - offset(width, available, millis), y, color);
        }
    }
}
