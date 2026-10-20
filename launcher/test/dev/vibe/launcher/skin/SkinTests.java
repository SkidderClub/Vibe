package dev.vibe.launcher.skin;

import dev.vibe.launcher.Check;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/** Skin normalisation and the software player renderer. */
public final class SkinTests {
    private SkinTests() { }

    public static void run(Check check) {
        check.test("legacy 64x32 skins gain mirrored left limbs", () -> {
            BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
            // Right arm front face at (44,20), 4x12; left arm front in 64x64 layout is at (36,52).
            for (int y = 20; y < 32; y++) for (int x = 44; x < 48; x++) legacy.setRGB(x, y, x == 44 ? 0xFFFF0000 : 0xFF00FF00);
            // An all-opaque hat layer is treated as missing, as in Minecraft.
            for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) legacy.setRGB(x, y, 0xFF123456);
            BufferedImage skin = SkinService.normalize(legacy);
            Check.equal(64, skin.getHeight());
            Check.equal(0xFFFF0000, skin.getRGB(39, 52));
            Check.equal(0xFF00FF00, skin.getRGB(36, 52));
            Check.equal(0, skin.getRGB(40, 8) >>> 24);
        });

        check.test("invalid textures are rejected", () -> {
            Check.equal(null, SkinService.normalize(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)));
            Check.equal(null, SkinService.normalize(null));
        });

        check.test("player model renders inside its reported bounds", () -> {
            SkinService.Skin skin = new SkinService.Skin(DefaultSkin.paint(), false, null, true);
            BufferedImage canvas = new BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            Rectangle2D bounds = new PlayerModel(skin).render(g, 100, 150, 6, 0.4, 0.2, 1.0);
            g.dispose();
            Check.isTrue(bounds.getHeight() > 32 * 6 * 0.9 && bounds.getHeight() < 32 * 6 * 1.2, "height " + bounds.getHeight());
            Check.isTrue(bounds.getCenterX() > 80 && bounds.getCenterX() < 120, "centred " + bounds.getCenterX());
            int painted = 0;
            for (int y = 0; y < canvas.getHeight(); y++) for (int x = 0; x < canvas.getWidth(); x++) if ((canvas.getRGB(x, y) >>> 24) != 0) painted++;
            Check.isTrue(painted > 4000, "model painted " + painted + " pixels");
            Check.equal(0, canvas.getRGB(2, 2) >>> 24);
        });
    }
}
