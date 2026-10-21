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
    private final NeverLoseFont regularTitle = new NeverLoseFont(Font.PLAIN, 20);
    private final NeverLoseFont regularState = new NeverLoseFont(Font.PLAIN, 16);
    private final ResourceLocation[] icons = new ResourceLocation[3];

    public void draw(ModuleNotifications.Entry entry, int x, int y, int width, float opacity,
                     long now, String theme, NotificationSettings settings, Runnable glassSurface) {
        if (opacity <= 0) return;
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        try {
            int height = settings.height.getInt();
            boolean square = "Skeet".equalsIgnoreCase(theme);
            boolean glass = "LiquidGlass".equalsIgnoreCase(theme);
            int radius = square ? 0 : Math.min(settings.radius.getInt(), height / 2);
            float surfaceOpacity = settings.opacity.getFloat() * opacity;
            int accent = entry.enabled ? settings.enabledColor.getArgb() : settings.disabledColor.getArgb();
            int background = settings.background.getArgb();
            if (settings.shadow.isEnabled()) {
                rounded(x - 1, y + 1, width + 2, height + 2, radius + (square ? 0 : 1), 0xFF000000, 24, surfaceOpacity);
                rounded(x, y + 2, width, height, radius, 0xFF000000, 40, surfaceOpacity);
            }
            if (glassSurface != null) glassSurface.run();
            else if (square) {
                int inset = settings.border.isEnabled() ? 2 : 0;
                if (inset > 0) {
                    Gui.drawRect(x, y, x + width, y + height, color(0xFF08090B, 255, surfaceOpacity));
                    Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, color(0xFF48494F, 255, surfaceOpacity));
                }
                int fill = RenderUtils.alpha(RenderUtils.blend(background, 0xFF070809, .35F), background >>> 24);
                Gui.drawRect(x + inset, y + inset, x + width - inset, y + height - inset, tint(fill, surfaceOpacity));
                Gui.drawRect(x + 3, y + 2, x + width - 3, y + 3, tint(accent, opacity));
            } else if (!glass) {
                int inset = settings.border.isEnabled() ? 1 : 0;
                if (inset > 0) rounded(x, y, width, height, radius, 0xFF515C6C, 150, surfaceOpacity);
                MenuRoundedRenderer.rect(x + inset, y + inset, width - inset * 2, height - inset * 2,
                        Math.max(0, radius - inset), tint(background, surfaceOpacity));
            }
            if (glass && settings.border.isEnabled()) {
                RenderUtils.roundedOutline(x, y, x + width, y + height, radius, 1,
                        color(0xFFD9E9FF, 85, surfaceOpacity));
            }

            float centerY = y + (height - (settings.progress.isEnabled() ? 2 : 0)) / 2F;
            if (settings.icons.isEnabled()) {
                int iconY = Math.round(centerY - 11);
                if (!square) rounded(x + 10, iconY, 22, 22, 11, 0xFF2A323C, glass ? 160 : 235, opacity);
                drawIcon(entry.message ? 2 : entry.enabled ? 1 : 0, x + 10, iconY, tint(accent, opacity));
            }

            NeverLoseFont nameFont = settings.font.is("Regular") ? regularTitle : title;
            NeverLoseFont stateFont = settings.font.is("Regular") ? regularState : state;
            int textLeft = x + (settings.icons.isEnabled() ? 42 : 12);
            int textRight = x + width - 12;
            if (settings.status.isEnabled() && !entry.message) {
                String label = LanguageManager.translate(entry.enabled ? "Enabled" : "Disabled");
                int pillWidth = Math.round(stateFont.width(label)) + (square ? 0 : 14);
                int pillX = x + width - 10 - pillWidth;
                if (!square) {
                    int pillColor = RenderUtils.blend(background, accent, .22F);
                    rounded(pillX, Math.round(centerY - 8), pillWidth, 16, 8, pillColor, glass ? 215 : 200, opacity);
                }
                centered(stateFont, label, pillX + (pillWidth - stateFont.width(label)) / 2F, centerY, tint(accent, opacity));
                textRight = pillX - 8;
            }
            String name = nameFont.fit(entry.name, Math.max(0, textRight - textLeft));
            float nameY = centerY - (entry.message ? 6 : 0);
            if (glass) centered(nameFont, name, textLeft + .5F, nameY + .5F, color(0xFF000000, 140, opacity));
            centered(nameFont, name, textLeft, nameY, tint(settings.text.getArgb(), opacity));
            if (entry.message) {
                String detail = regularState.fit(entry.detail, Math.max(0, textRight - textLeft));
                centered(regularState, detail, textLeft, centerY + 6, tint(RenderUtils.blend(settings.text.getArgb(), accent, .22F), opacity));
            }

            if (settings.progress.isEnabled()) {
                int inset = square ? 4 : 11;
                int trackWidth = width - inset * 2;
                Gui.drawRect(x + inset, y + height - 5, x + width - inset, y + height - 4,
                        color(0xFFFFFFFF, 18, opacity));
                int progress = Math.round(trackWidth * entry.remaining(now));
                if (progress > 0) Gui.drawRect(x + inset, y + height - 5, x + inset + progress, y + height - 4,
                        tint(accent, .65F * opacity));
            }
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            if (alphaTest) GlStateManager.enableAlpha();
        }
    }

    private static void centered(NeverLoseFont font, String text, float x, float centerY, int color) {
        font.draw(text, x, centerY - font.inkHeight(text) / 2F - font.inkTop(text), color);
    }
    private static int color(int rgb, int alpha, float opacity) {
        return RenderUtils.alpha(rgb, Math.round(alpha * opacity));
    }
    private static int tint(int argb, float opacity) { return color(argb, argb >>> 24, opacity); }
    private static void rounded(int x, int y, int width, int height, int radius, int rgb, int alpha, float opacity) {
        MenuRoundedRenderer.rect(x, y, width, height, radius, color(rgb, alpha, opacity));
    }
    private void drawIcon(int index, int x, int y, int color) {
        Minecraft mc = Minecraft.getMinecraft();
        if (icons[index] == null) {
            BufferedImage image = new BufferedImage(88, 88, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(4, 4); g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float path = new Path2D.Float();
            if (index == 2) {
                path.moveTo(5, 6.5); path.lineTo(17, 6.5); path.lineTo(17, 15.5); path.lineTo(5, 15.5); path.closePath();
                path.moveTo(5.5, 7.5); path.lineTo(11, 12); path.lineTo(16.5, 7.5);
            } else if (index == 1) {
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
