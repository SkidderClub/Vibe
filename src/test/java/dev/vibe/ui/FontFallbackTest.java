package dev.vibe.ui;

import java.awt.Font;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Android launchers register no CJK fallback for AWT logical fonts, so themed
 * menus would draw empty boxes for every translated label. The bundled Noto
 * face must be selected whenever the primary face cannot draw the text.
 */
public class FontFallbackTest {
    @Test
    public void missingGlyphsFallBackToTheBundledCjkFace() {
        Font primary = new Font("Segoe UI", Font.PLAIN, 12);
        for (String text : new String[] {"中文菜单", "日本語", "한국어", "Русский"}) {
            Font chosen = NeverLoseFont.forText(primary, text);
            assertEquals("Cannot render " + text, -1, chosen.canDisplayUpTo(text));
        }
    }

    @Test
    public void supportedTextKeepsThePrimaryFace() {
        Font primary = new Font("Segoe UI", Font.PLAIN, 12);
        assertSame(primary, NeverLoseFont.forText(primary, "Vibe v0.0.7"));
    }
}
