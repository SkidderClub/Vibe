package dev.vibe.hud;

import dev.vibe.module.impl.client.HudModule;
import dev.vibe.ui.RenderUtils;
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
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        int accent = settings.accent.getArgb();
        int ink = settings.text.getArgb();
        boolean shadow = settings.shadow.isEnabled();
        int headerY = y + (header - 2) / 2;
        int titleLeft = x + (width - font.getStringWidth(MatchCountdowns.TITLE) - 14) / 2;
        clock(titleLeft + 4, headerY, accent);
        int rail = RenderUtils.alpha(accent, 80);
        Gui.drawRect(x + 8, y + header - 3, x + width - 8, y + header - 2, rail);
        // The shared glass and outline passes can restore the driver's blend state independently of Minecraft's cache.
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        font.drawString(MatchCountdowns.TITLE, titleLeft + 14, headerY - font.FONT_HEIGHT / 2, ink, shadow);

        for (int i = 0; i < rows; i++) {
            MatchCountdowns.Timer timer = timers.get(i);
            int rowY = y + header + i * rowHeight;
            if (adjust) {
                Gui.drawRect(x + 7, rowY + 1, x + width - 7, rowY + rowHeight, 0x2A07070D);
                Gui.drawRect(x + 7, rowY + 2, x + 9, rowY + rowHeight - 1, rail);
            }
            String time = timer.label(now);
            int timeX = x + width - 11 - font.getStringWidth(time);
            int textX = x + (adjust ? 15 : 12);
            int textY = rowY + (rowHeight - font.FONT_HEIGHT) / 2;
            if (timer.kind != MatchCountdowns.Kind.MATCH) {
                String type = timer.kind.badge;
                int pillWidth = font.getStringWidth(type) + 8;
                HudShapes.round(textX - 2, rowY + 2, pillWidth, rowHeight - 4, skeet ? 1 : 3, RenderUtils.alpha(accent, 35));
                font.drawString(type, textX + 2, textY, accent, shadow);
                textX += pillWidth + 3;
            }
            String name = fit(font, timer.name, timeX - textX - 8);
            font.drawString(name, textX, textY, ink, shadow);
            font.drawString(time, timeX, textY, accent, shadow);
        }
        if (adjust) Gui.drawRect(x + 4, y + height - 2, x + width - 4, y + height - 1, accent);
    }

    private static void clock(int x, int y, int color) {
        RenderUtils.roundedOutline(x - 4, y - 4, x + 4, y + 4, 4, 1F, color);
        HudShapes.line(x, y - 2, x, y, 1F, color);
        HudShapes.line(x, y, x + 1.5F, y + 1, 1F, color);
    }

    private static String fit(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) return text;
        String end = "...";
        int available = width - font.getStringWidth(end);
        return available > 0 ? font.trimStringToWidth(text, available) + end : "";
    }
}
