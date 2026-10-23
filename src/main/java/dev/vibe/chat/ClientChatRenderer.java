package dev.vibe.chat;

import java.awt.Color;
import net.minecraft.client.gui.FontRenderer;

/** Colors the local prefix at draw time, including messages already in history. */
public final class ClientChatRenderer {
    public static final int UNHANDLED = Integer.MIN_VALUE;
    private static final ThreadLocal<Boolean> DRAWING = new ThreadLocal<Boolean>();

    private ClientChatRenderer() { }

    public static int draw(Object renderer, String text, float x, float y, int color, boolean shadow) {
        return drawAt(renderer, text, x, y, color, shadow, System.currentTimeMillis());
    }

    public static int drawAt(Object renderer, String text, float x, float y, int color, boolean shadow, long time) {
        if (Boolean.TRUE.equals(DRAWING.get()) || !(renderer instanceof FontRenderer) || text == null) return UNHANDLED;
        int prefix = text.indexOf(ClientChat.PREFIX);
        if (prefix < 0) return UNHANDLED;
        FontRenderer font = (FontRenderer) renderer;
        int name = prefix + ClientChat.PREFIX.indexOf("Vibe");
        // Match vanilla's treatment of a color supplied without an alpha byte.
        int alpha = (color & 0xFC000000) == 0 ? 0xFF000000 : color & 0xFF000000;
        DRAWING.set(Boolean.TRUE);
        try {
            String before = text.substring(0, name);
            font.drawString(before, x, y, color, shadow);
            x += font.getStringWidth(before);
            for (int i = 0; i < 4; i++) {
                String letter = text.substring(name + i, name + i + 1);
                font.drawString(letter, x, y, alpha | rainbow(time, i), shadow);
                x += font.getStringWidth(letter);
            }
            return font.drawString(text.substring(name + 4), x, y, color, shadow);
        } finally {
            DRAWING.remove();
        }
    }

    static int rainbow(long time, int letter) {
        float hue = Math.floorMod(time, 6000L) / 6000.0F + letter * 0.085F;
        return Color.HSBtoRGB(hue, 0.65F, 1.0F) & 0xFFFFFF;
    }
}
