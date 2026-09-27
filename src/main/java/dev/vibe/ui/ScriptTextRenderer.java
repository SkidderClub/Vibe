package dev.vibe.ui;

import java.awt.Font;
import dev.vibe.language.LanguageManager;

/** Draws catalog-backed custom scripts with their bundled display fonts. */
public final class ScriptTextRenderer {
    public static final int UNHANDLED = Integer.MIN_VALUE;
    private static final NeverLoseFont FONT = new NeverLoseFont(Font.PLAIN, 20);

    private ScriptTextRenderer() { }

    /** Called from the FontRenderer core hook; renderer is intentionally Object for obfuscated runtime compatibility. */
    public static int draw(Object renderer, String text, float x, float y, int color, boolean shadow) {
        if (!LanguageManager.isScriptFontText(text)) return UNHANDLED;
        color = opaque(color);
        if (shadow) FONT.draw(text, x + 1F, y + 1F, (color & 0xFF000000) | 0x25000000);
        FONT.draw(text, x, y, color);
        return Math.round(FONT.width(text));
    }

    /** Matches the dedicated script font when a layout asks FontRenderer for a catalog label's width. */
    public static int width(String text) {
        return LanguageManager.isScriptFontText(text) ? Math.round(FONT.width(text)) : UNHANDLED;
    }

    private static int opaque(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
