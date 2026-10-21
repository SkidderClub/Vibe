package dev.vibe.feedback;

import dev.vibe.language.LanguageManager;
import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.RenderUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Compact cards with optical text alignment and icons rasterized at four times their display size. */
public final class NotificationRenderer {
    public static final int WIDTH = 190, HEIGHT = 38, GAP = 7, STRIDE = HEIGHT + GAP;
    private final NeverLoseFont title = new NeverLoseFont(Font.BOLD, 20);
    private final NeverLoseFont state = new NeverLoseFont(Font.BOLD, 16);
    private final ResourceLocation[] icons = new ResourceLocation[2];

    public void draw(ModuleNotifications.Entry entry, int x, int y, int width, float opacity,
                     long now, String theme, Runnable glassSurface) {
        if (opacity <= 0) return;
        boolean square = "Skeet".equalsIgnoreCase(theme);
        if (!square) {
            rounded(x - 1, y + 1, width + 2, HEIGHT + 2, 10, 0xFF000000, 24, opacity);
            rounded(x, y + 2, width, HEIGHT, 9, 0xFF000000, 40, opacity);
        }
        if (glassSurface != null) glassSurface.run();
        else if (square) {
            Gui.drawRect(x, y, x + width, y + HEIGHT, color(0xFF3A3B41, 225, opacity));
            Gui.drawRect(x + 1, y + 1, x + width - 1, y + HEIGHT - 1, color(0xFF18191D, 250, opacity));
        } else {
            rounded(x, y, width, HEIGHT, 9, 0xFF444C58, 185, opacity);
            rounded(x + 1, y + 1, width - 2, HEIGHT - 2, 8, 0xFF181D25, 248, opacity);
        }

        int accent = entry.enabled ? 0xFF89DDB6 : 0xFFEBADB3;
        rounded(x + 10, y + 7, 22, 22, 11, 0xFF2A323C, 235, opacity);
        drawIcon(entry.enabled, x + 10, y + 7, color(accent, 255, opacity));

        String label = LanguageManager.translate(entry.enabled ? "Enabled" : "Disabled");
        int pillWidth = Math.round(state.width(label)) + 14;
        int pillX = x + width - 10 - pillWidth;
        rounded(pillX, y + 10, pillWidth, 16, 8,
                entry.enabled ? 0xFF284B3C : 0xFF4E333C, 185, opacity);
        centered(state, label, pillX + (pillWidth - state.width(label)) / 2F, y + 18,
                color(accent, 255, opacity));
        String name = title.fit(entry.name, Math.max(8, pillX - (x + 42) - 8));
        centered(title, name, x + 42, y + 18, color(0xFFF0F3F8, 255, opacity));

        int trackWidth = width - 22;
        Gui.drawRect(x + 11, y + HEIGHT - 5, x + width - 11, y + HEIGHT - 4,
                color(0xFFFFFFFF, 18, opacity));
        int progress = Math.round(trackWidth * entry.remaining(now));
        if (progress > 0) Gui.drawRect(x + 11, y + HEIGHT - 5, x + 11 + progress, y + HEIGHT - 4,
                color(accent, 145, opacity));
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void centered(NeverLoseFont font, String text, float x, float centerY, int color) {
        font.draw(text, x, centerY - font.inkHeight(text) / 2F - font.inkTop(text), color);
    }
    private static int color(int rgb, int alpha, float opacity) {
        return RenderUtils.alpha(rgb, Math.round(alpha * opacity));
    }
    private static void rounded(int x, int y, int width, int height, int radius, int rgb, int alpha, float opacity) {
        MenuRoundedRenderer.rect(x, y, width, height, radius, color(rgb, alpha, opacity));
    }
    private void drawIcon(boolean enabled, int x, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        int index = enabled ? 1 : 0;
        if (icons[index] == null) {
            BufferedImage image = new BufferedImage(88, 88, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(4, 4); g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float path = new Path2D.Float();
            if (enabled) {
                path.moveTo(6.5, 11); path.lineTo(9.5, 14); path.lineTo(15.5, 8);
            } else { path.moveTo(7, 11); path.lineTo(15, 11); }
            g.draw(path); g.dispose();
            icons[index] = mc.getTextureManager().getDynamicTextureLocation("vibe_notification_" + index, new DynamicTexture(image));
        }
        GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        mc.getTextureManager().bindTexture(icons[index]);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, (color >>> 24) / 255F);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 88, 88, 22, 22, 88, 88);
        GlStateManager.color(1, 1, 1, 1);
        if (alphaTest) GlStateManager.enableAlpha();
    }
}
