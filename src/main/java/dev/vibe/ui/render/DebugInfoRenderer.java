package dev.vibe.ui.render;

import dev.vibe.Vibe;
import dev.vibe.debuginfo.DebugInfoEntry;
import dev.vibe.debuginfo.DebugInfoSnapshot;
import dev.vibe.hud.HudShapes;
import dev.vibe.module.impl.client.DebugInfoModule;
import dev.vibe.ui.DebugOverlay;
import dev.vibe.ui.GuiRenderState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Renders the {@link DebugInfoModule} two-column telemetry overlay card. */
public final class DebugInfoRenderer {

    private static final int PAD_X = 7;
    private static final int PAD_Y = 6;
    private static final int COLUMN_GAP = 14;
    private static final int ROW_HEIGHT = 10;
    private static final int HEADER_HEIGHT = 13;
    private static final int LABEL_COLOR = 0xFF9AA8BA;
    private static final int HEADER_TEXT_COLOR = 0xFFF8FAFC;

    private final Minecraft minecraft = Minecraft.getMinecraft();

    public void render() {
        Vibe vibe = Vibe.getInstance();
        if (vibe == null || vibe.getModuleManager() == null) {
            return;
        }
        DebugInfoModule module = vibe.getModuleManager().getModule(DebugInfoModule.class);
        if (module == null || !module.isEnabled() || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return;
        }
        if (module.getHideInF3().isEnabled() && minecraft.gameSettings.showDebugInfo) {
            return;
        }
        DebugInfoSnapshot snapshot = module.sample();
        if (snapshot == null) {
            return;
        }
        List<DebugInfoEntry> entries = snapshot.buildEntries(module);
        if (entries.isEmpty()) {
            return;
        }

        FontRenderer font = minecraft.fontRendererObj;
        boolean showHeader = module.getShowHeader().isEnabled();
        String headerTitle = "DEBUG INFO";

        int maxLabelWidth = 0;
        int maxValueWidth = 0;
        for (DebugInfoEntry entry : entries) {
            maxLabelWidth = Math.max(maxLabelWidth, font.getStringWidth(entry.label));
            maxValueWidth = Math.max(maxValueWidth, font.getStringWidth(entry.value));
        }

        int contentWidth = maxLabelWidth + COLUMN_GAP + maxValueWidth;
        if (showHeader) {
            contentWidth = Math.max(contentWidth, font.getStringWidth(headerTitle) + 10);
        }

        int panelWidth = contentWidth + PAD_X * 2;
        int panelHeight = PAD_Y * 2 + entries.size() * ROW_HEIGHT + (showHeader ? HEADER_HEIGHT : 0);

        float scale = module.getScale().getFloat();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int screenWidth = resolution.getScaledWidth();
        int screenHeight = resolution.getScaledHeight();

        float scaledWidth = panelWidth * scale;
        float scaledHeight = panelHeight * scale;
        float offsetX = module.getOffsetX().getFloat();
        float offsetY = module.getOffsetY().getFloat();

        String pos = module.getPosition().getValue();
        boolean alignRight = DebugInfoModule.POS_TOP_RIGHT.equals(pos)
                || DebugInfoModule.POS_BOTTOM_RIGHT.equals(pos);
        boolean alignBottom = DebugInfoModule.POS_BOTTOM_LEFT.equals(pos)
                || DebugInfoModule.POS_BOTTOM_RIGHT.equals(pos);

        float x = alignRight ? screenWidth - scaledWidth - offsetX : offsetX;
        float y = alignBottom ? screenHeight - scaledHeight - offsetY : offsetY;
        x = Math.max(2.0F, Math.min(screenWidth - scaledWidth - 2.0F, x));
        y = Math.max(2.0F, Math.min(screenHeight - scaledHeight - 2.0F, y));

        if (DebugOverlay.overlaps(x, y, x + scaledWidth, y + scaledHeight)) {
            return;
        }

        int accent = module.getAccentColor().getArgb();
        int bgAlpha = module.getBackgroundAlpha().getInt();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GlStateManager.pushMatrix();
        try {
            GuiRenderState.prepare(false);
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);

            if (module.getShowBackground().isEnabled() && bgAlpha > 0) {
                int bgColor = (bgAlpha << 24) | 0x0B0F17;
                int borderAlpha = Math.min(255, (int) Math.round(bgAlpha * 0.45D));
                int borderColor = (borderAlpha << 24) | (accent & 0x00FFFFFF);
                HudShapes.round(-0.75F, -0.75F, panelWidth + 1.5F, panelHeight + 1.5F, 5.5F, borderColor);
                HudShapes.round(0.0F, 0.0F, panelWidth, panelHeight, 5.0F, bgColor);
            }

            int cursorY = PAD_Y;
            if (showHeader) {
                Gui.drawRect(PAD_X, cursorY + 1, PAD_X + 2, cursorY + 8, accent);
                font.drawStringWithShadow(headerTitle, PAD_X + 6, cursorY, HEADER_TEXT_COLOR);
                int dividerColor = ((Math.max(40, bgAlpha / 3)) << 24) | (accent & 0x00FFFFFF);
                Gui.drawRect(PAD_X, cursorY + 10, panelWidth - PAD_X, cursorY + 11, dividerColor);
                cursorY += HEADER_HEIGHT;
            }

            int rightEdge = panelWidth - PAD_X;
            for (DebugInfoEntry entry : entries) {
                font.drawStringWithShadow(entry.label, PAD_X, cursorY, LABEL_COLOR);
                int valueWidth = font.getStringWidth(entry.value);
                font.drawStringWithShadow(entry.value, rightEdge - valueWidth, cursorY, entry.valueColor);
                cursorY += ROW_HEIGHT;
            }
        } finally {
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GuiRenderState.prepare(false);
        }
    }
}
