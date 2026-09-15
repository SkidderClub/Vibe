package dev.vibe.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** Shared presentation for account screens and the main menu. */
final class AccountScreenStyle {
    static int BACKGROUND, SURFACE, BORDER, MUTED, ACCENT, TINT, HOVER;
    static final int TEXT = 0xFFF3F1FA;
    static final int SUCCESS = 0xFF7DDDC3;
    static final int ERROR = 0xFFFF969F;

    static { applyTheme(MenuThemes.current()); }

    static void applyTheme(MenuThemes.Preset preset) {
        BACKGROUND = preset.background;
        SURFACE = preset.surface;
        ACCENT = preset.accent;
        MUTED = preset.muted;
        BORDER = RenderUtils.blend(SURFACE, ACCENT, 0.18F);
        TINT = RenderUtils.blend(SURFACE, ACCENT, 0.12F);
        HOVER = RenderUtils.blend(SURFACE, ACCENT, 0.18F);
    }

    private AccountScreenStyle() { }

    static void panel(int x, int y, int width, int height, int fill, int border) {
        MenuRoundedRenderer.rect(x, y, width, height, 10, border);
        MenuRoundedRenderer.rect(x + 1, y + 1, width - 2, height - 2, 9, fill);
    }

    static void window(int x, int y, int width, int height) {
        MenuRoundedRenderer.rect(x - 3, y + 4, width + 6, height + 1, 16, 0x55000000);
        MenuRoundedRenderer.rect(x, y, width, height, 14, BORDER);
        MenuRoundedRenderer.rect(x + 1, y + 1, width - 2, height - 2, 13, BACKGROUND);
    }

    static void text(String value, int x, int y, int color) {
        value = dev.vibe.language.LanguageManager.translate(value);
        rawText(value, x, y, color);
    }

    static void rawText(String value, int x, int y, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawString(value, x, y, color);
    }

    static String fit(String value, int width) {
        value = dev.vibe.language.LanguageManager.translate(value);
        return fitRaw(value, width);
    }

    static String fitRaw(String value, int width) {
        return Minecraft.getMinecraft().fontRendererObj.trimStringToWidth(value, Math.max(0, width));
    }

    static void title(String value, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(1.5F, 1.5F, 1);
        text(value, 0, 0, TEXT);
        GlStateManager.popMatrix();
    }

    static class Button extends GuiButton {
        private final String subtitle;
        private final boolean primary;
        private final boolean danger;

        Button(int id, int x, int y, int width, int height, String label, String subtitle, boolean primary, boolean danger) {
            super(id, x, y, width, height, label);
            this.subtitle = subtitle;
            this.primary = primary;
            this.danger = danger;
        }

        @Override public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) return;
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            int fill = !enabled ? BACKGROUND : hovered ? HOVER : primary ? TINT : SURFACE;
            int border = !enabled ? SURFACE : hovered || primary ? RenderUtils.blend(BORDER, ACCENT, 0.5F) : BORDER;
            panel(xPosition, yPosition, width, height, fill, border);
            int color = !enabled ? RenderUtils.blend(SURFACE, MUTED, 0.6F) : danger ? ERROR : TEXT;
            String label = fit(displayString, width - 14);
            if (subtitle.isEmpty()) {
                text(label, xPosition + (width - mc.fontRendererObj.getStringWidth(label)) / 2, yPosition + (height - 8) / 2, color);
            } else {
                text(label, xPosition + 12, yPosition + 9, color);
                text(fit(subtitle, width - 24), xPosition + 12, yPosition + 23,
                        !enabled ? RenderUtils.blend(SURFACE, MUTED, 0.6F) : MUTED);
            }
            mouseDragged(mc, mouseX, mouseY);
        }
    }
}
