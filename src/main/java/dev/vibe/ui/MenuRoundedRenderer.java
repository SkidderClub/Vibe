package dev.vibe.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** One antialiased nine-slice mask keeps circular corners smooth at every GUI scale. */
final class MenuRoundedRenderer {
    private static ResourceLocation mask;

    private MenuRoundedRenderer() { }

    static void rect(int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mask == null) {
            BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(0, 0, 96, 96, 64, 64);
            graphics.dispose();
            mask = mc.getTextureManager().getDynamicTextureLocation("vibe_menu_rounding", new DynamicTexture(image));
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        mc.getTextureManager().bindTexture(mask);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        int[] xs = {x, x + r, x + width - r, x + width};
        int[] ys = {y, y + r, y + height - r, y + height};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Gui.drawScaledCustomSizeModalRect(xs[column], ys[row], column * 32, row * 32, 32, 32,
                        xs[column + 1] - xs[column], ys[row + 1] - ys[row], 96, 96);
            }
        }
        GlStateManager.color(1, 1, 1, 1);
        if (alphaTest) GlStateManager.enableAlpha();
    }
}
