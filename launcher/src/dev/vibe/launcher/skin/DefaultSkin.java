package dev.vibe.launcher.skin;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** A Steve-like skin painted in code, used before Minecraft's own assets have been downloaded. */
final class DefaultSkin {
    private static final Color SKIN = new Color(0xC69680), SKIN_DARK = new Color(0xAD7D66), HAIR = new Color(0x3B2A1D),
            SHIRT = new Color(0x2FA3A8), SHIRT_DARK = new Color(0x268A8F), PANTS = new Color(0x3F3A93), SHOES = new Color(0x5A5A5A),
            EYE = new Color(0xFFFFFF), IRIS = new Color(0x4A4ACF), MOUTH = new Color(0x6B4336);

    private DefaultSkin() { }

    static BufferedImage paint() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // Head: top, bottom, then right/front/left/back sides.
        fill(g, 8, 0, 8, 8, HAIR);
        fill(g, 16, 0, 8, 8, SKIN_DARK);
        fill(g, 0, 8, 32, 8, SKIN);
        fill(g, 0, 8, 32, 2, HAIR);
        fill(g, 24, 8, 8, 8, HAIR);
        fill(g, 0, 10, 2, 3, HAIR);
        fill(g, 22, 10, 2, 3, HAIR);
        fill(g, 8, 12, 2, 1, EYE); fill(g, 10, 12, 1, 1, IRIS);
        fill(g, 13, 12, 1, 1, IRIS); fill(g, 14, 12, 2, 1, EYE);
        fill(g, 11, 13, 2, 1, SKIN_DARK);
        fill(g, 10, 14, 4, 1, MOUTH);
        // Body.
        fill(g, 16, 16, 24, 16, SHIRT);
        fill(g, 20, 16, 16, 4, SHIRT_DARK);
        // Arms (right at 40,16 and left at 32,48): sleeves over skin.
        paintLimb(g, 40, 16, SKIN, SHIRT, 4);
        paintLimb(g, 32, 48, SKIN, SHIRT, 4);
        // Legs (right at 0,16 and left at 16,48): trousers and shoes.
        paintLimb(g, 0, 16, PANTS, PANTS, 0);
        paintLimb(g, 16, 48, PANTS, PANTS, 0);
        fill(g, 0, 29, 16, 3, SHOES);
        fill(g, 16, 61, 16, 3, SHOES);
        g.dispose();
        return image;
    }

    private static void paintLimb(Graphics2D g, int u, int v, Color base, Color top, int sleeve) {
        fill(g, u + 4, v, 8, 4, top);
        fill(g, u, v + 4, 16, 12, base);
        if (sleeve > 0) fill(g, u, v + 4, 16, sleeve, top);
    }

    private static void fill(Graphics2D g, int x, int y, int w, int h, Color color) {
        g.setColor(color);
        g.fillRect(x, y, w, h);
    }
}
