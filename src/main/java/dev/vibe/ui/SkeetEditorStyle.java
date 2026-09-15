package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.impl.BlurModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

/**
 * Shared flat Gamesense/Skeet surface used by Vibe's utility editors. Keeping
 * it in one place prevents the editors from slowly drifting into six separate
 * visual languages while retaining their existing interactions and layouts.
 */
public final class SkeetEditorStyle {
    public static final int WINDOW = 0xFF161616;
    public static final int SURFACE = 0xFF101011;
    public static final int FIELD = 0xFF0C0C0D;
    public static final int BORDER = 0xFF292A31;
    public static final int TEXT = 0xFFE8E8EB;
    public static final int MUTED = 0xFF9A9BA1;
    private static final ParticlesRenderer PARTICLES = new ParticlesRenderer();

    private SkeetEditorStyle() { }

    public static void backdrop(GuiScreen screen, String blurElement, float partialTicks) {
        BlurModule blur = Vibe.getInstance().getModuleManager().getModule(BlurModule.class);
        if (blur != null && blur.isEnabled() && blur.getElements().isSelected(blurElement)) {
            KawaseBlur.drawBackdrop(screen.width, screen.height, blur.getStrength().getInt(), partialTicks);
        } else {
            Gui.drawRect(0, 0, screen.width, screen.height, 0xD90A0A0D);
        }
        PARTICLES.draw(screen);
    }

    public static void window(int left, int top, int right, int bottom, String title, String detail) {
        title = dev.vibe.language.LanguageManager.translate(title);
        detail = dev.vibe.language.LanguageManager.translate(detail);
        Gui.drawRect(left - 1, top - 6, right + 1, bottom + 1, 0xFF090909);
        Gui.drawRect(left, top - 5, right, bottom, WINDOW);
        border(left, top - 5, right, bottom, BORDER);
        accentLine(left + 1, top - 4, right - 1);
        Gui.drawRect(left + 1, top + 20, right - 1, top + 21, BORDER);
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.fontRendererObj.drawStringWithShadow(title, left + 9, top + 4, TEXT);
        if (detail != null && !detail.trim().isEmpty()) {
            int max = Math.max(0, right - left - 26 - minecraft.fontRendererObj.getStringWidth(title));
            String text = minecraft.fontRendererObj.trimStringToWidth(detail, max);
            minecraft.fontRendererObj.drawStringWithShadow(text, right - 9 - minecraft.fontRendererObj.getStringWidth(text), top + 4, MUTED);
        }
    }

    public static void panel(int left, int top, int right, int bottom, String caption) {
        caption = dev.vibe.language.LanguageManager.translate(caption);
        Gui.drawRect(left, top, right, bottom, SURFACE);
        border(left, top, right, bottom, BORDER);
        if (caption != null && !caption.isEmpty()) {
            Gui.drawRect(left + 1, top + 1, right - 1, top + 15, 0xFF131314);
            Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(caption, left + 7, top + 5, MUTED);
            Gui.drawRect(left + 1, top + 15, right - 1, top + 16, 0xFF202126);
        }
    }

    public static void row(int left, int top, int right, int bottom, boolean active, boolean hovered) {
        Gui.drawRect(left, top, right, bottom, active ? 0xFF252329 : hovered ? 0xFF1A1A1D : FIELD);
        border(left, top, right, bottom, active ? accent(0.18F) : 0xFF1D1E22);
    }

    public static void input(int left, int top, int right, int bottom) {
        Gui.drawRect(left, top, right, bottom, FIELD);
        border(left, top, right, bottom, 0xFF313239);
    }

    public static void button(int left, int top, int right, int bottom, String text, boolean active) {
        text = dev.vibe.language.LanguageManager.translate(text);
        int fill = active ? accent(0.14F) : 0xFF202025;
        Gui.drawRect(left, top, right, bottom, fill);
        border(left, top, right, bottom, active ? accent(0.14F) : 0xFF35363C);
        int color = active ? 0xFF101012 : TEXT;
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                left + (right - left - Minecraft.getMinecraft().fontRendererObj.getStringWidth(text)) / 2,
                top + Math.max(2, (bottom - top - 8) / 2), color);
    }

    public static int accent(float phase) {
        // The original Skeet style is mostly neutral; a controlled shifting
        // accent preserves Vibe's palette without bringing back neon panels.
        return RenderUtils.blend(0xFFB14DFF, 0xFF48D6C5,
                (float) ((Math.sin(System.currentTimeMillis() / 950.0D + phase) + 1.0D) * 0.5D));
    }

    public static void border(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    private static void accentLine(int left, int top, int right) {
        int length = Math.max(1, right - left);
        for (int x = left; x < right; x += 3) {
            float phase = (x - left) / (float) length;
            Gui.drawRect(x, top, Math.min(right, x + 3), top + 2, accent(phase));
        }
    }
}
