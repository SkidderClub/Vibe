package dev.vibe.hud;

import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.NeverLoseFont;
import dev.vibe.ui.RenderUtils;
import dev.vibe.ui.adjust.AdjustFonts;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

final class MatchCountdownsRenderer {
    private MatchCountdownsRenderer() {}

    static void draw(HudManager manager, HudModule hud, HudManager.HudElement element, DashboardSettings settings,
                     ScaledResolution resolution, List<MatchCountdowns.Timer> timers, long now) {
        int width = settings.width.getInt();
        int rows = Math.min(timers.size(), settings.rows.getInt());
        if (rows == 0) {
            element.setBounds(element.left(resolution, width), element.top(resolution, 0), width, 0);
            return;
        }
        String theme = manager.theme(element, hud);
        boolean skeet = "Skeet".equals(theme);
        boolean adjust = "Adjust".equals(theme);
        int rowHeight = adjust ? 15 : 18;
        int header = adjust ? 20 : 24;
        int height = header + rows * rowHeight + 4;

        int x = element.left(resolution, width);
        int y = element.top(resolution, height);
        element.setBounds(x, y, width, height);

        HudOverlayRenderer.panel(manager, hud, element, settings, x, y, width, height);
        FontRenderer nativeFont = Minecraft.getMinecraft().fontRendererObj;
        NeverLoseFont body = adjust ? AdjustFonts.SMALL : NeverLoseFont.REGULAR;
        NeverLoseFont heading = adjust ? AdjustFonts.LABEL : NeverLoseFont.BOLD;
        int accent = settings.accent.getArgb();
        int ink = settings.text.getArgb();
        int top = y + (adjust ? 6 : 8);
        boolean shadow = settings.shadow.isEnabled();
        clock(x + 13, top + (adjust ? 3 : 4), accent);
        int rail = RenderUtils.alpha(accent, 80);
        Gui.drawRect(x + 8, y + header - 3, x + width - 8, y + header - 2, rail);
        // The shared glass and outline passes can restore the driver's blend state independently of Minecraft's cache.
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        text(nativeFont, heading, skeet, MatchCountdowns.TITLE, x + 22, top, ink, shadow);

        for (int i = 0; i < rows; i++) {
            MatchCountdowns.Timer timer = timers.get(i);
            int rowY = y + header + i * rowHeight;
            if (adjust) {
                Gui.drawRect(x + 7, rowY + 1, x + width - 7, rowY + rowHeight, 0x2A07070D);
                Gui.drawRect(x + 7, rowY + 2, x + 9, rowY + rowHeight - 1, rail);
            }
            String time = timer.label(now);
            float timeX = x + width - 11 - width(nativeFont, body, skeet, time);
            int textX = x + (adjust ? 15 : 12);
            int textY = rowY + (adjust ? 4 : 5);
            if (timer.kind != MatchCountdowns.Kind.MATCH) {
                NeverLoseFont badge = adjust ? AdjustFonts.HUD : AdjustFonts.SMALL;
                String type = timer.kind.badge;
                float pillWidth = width(nativeFont, badge, skeet, type) + 8;
                HudShapes.round(textX - 2, rowY + 2, pillWidth, rowHeight - 4, skeet ? 1 : 3, RenderUtils.alpha(accent, 35));
                text(nativeFont, badge, skeet, type, textX + 2, textY + (skeet ? 0 : 1), accent, shadow);
                textX += (int) Math.ceil(pillWidth) + 3;
            }
            String name = fit(nativeFont, body, skeet, timer.name, timeX - textX - 8);
            text(nativeFont, body, skeet, name, textX, textY, ink, shadow);
            text(nativeFont, body, skeet, time, timeX, textY, accent, shadow);
        }
        if (adjust) Gui.drawRect(x + 4, y + height - 2, x + width - 4, y + height - 1, accent);
    }

    private static void clock(int x, int y, int color) {
        RenderUtils.roundedOutline(x - 4, y - 4, x + 4, y + 4, 4, 1F, color);
        HudShapes.line(x, y - 2, x, y, 1F, color);
        HudShapes.line(x, y, x + 1.5F, y + 1, 1F, color);
    }

    private static float width(FontRenderer nativeFont, NeverLoseFont smooth, boolean skeet, String text) {
        return skeet ? nativeFont.getStringWidth(text) : smooth.width(text);
    }

    private static String fit(FontRenderer nativeFont, NeverLoseFont smooth, boolean skeet, String text, float width) {
        if (width(nativeFont, smooth, skeet, text) <= width) return text;
        String end = "…";
        while (!text.isEmpty() && width(nativeFont, smooth, skeet, text + end) > width) {
            text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
        }
        return text.isEmpty() ? "" : text + end;
    }

    private static void text(FontRenderer nativeFont, NeverLoseFont smooth, boolean skeet, String text,
                             float x, float y, int color, boolean shadow) {
        if (skeet) {
            nativeFont.drawString(text, x, y, color, shadow);
        } else {
            if (shadow) smooth.draw(text, x + .6F, y + .6F, (color >>> 25) << 24);
            smooth.draw(text, x, y, color);
        }
    }
}
