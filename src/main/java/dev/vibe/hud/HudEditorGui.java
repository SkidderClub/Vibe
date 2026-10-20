package dev.vibe.hud;

import static dev.vibe.ui.menu.AccountScreenStyle.ACCENT;
import static dev.vibe.ui.menu.AccountScreenStyle.BACKGROUND;
import static dev.vibe.ui.menu.AccountScreenStyle.BORDER;
import static dev.vibe.ui.menu.AccountScreenStyle.HOVER;
import static dev.vibe.ui.menu.AccountScreenStyle.MUTED;
import static dev.vibe.ui.menu.AccountScreenStyle.SUCCESS;
import static dev.vibe.ui.menu.AccountScreenStyle.SURFACE;
import static dev.vibe.ui.menu.AccountScreenStyle.TEXT;
import static dev.vibe.ui.menu.AccountScreenStyle.TINT;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.impl.client.HudModule;
import dev.vibe.module.impl.client.MusicModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import dev.vibe.ui.menu.AccountScreenStyle;
import dev.vibe.ui.GuiClip;
import dev.vibe.ui.KawaseBlur;
import dev.vibe.ui.MenuRoundedRenderer;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/** The HUD workspace in the menu and account screen style: elements, a live preview and the selected element's settings. */
public final class HudEditorGui extends GuiScreen {
    private static final String[] THEMES = {"Vibe", "Skeet", "LiquidGlass"};
    private static final int GAP = 4;
    private static final int LABEL = 15;
    private static final int ROW = 24;
    private static final int SLIDER_ROW = 36;
    private static final int TEXT_ROW = 42;
    private static final int OPTION = 18;
    private static final int PICKER = 96;
    private static final int IDENTITY = 34;
    private static final int CONTROL = 42;

    private final HudManager manager;
    private final Set<MultiSelectSetting> openMulti = new HashSet<MultiSelectSetting>();
    private final List<Guide> guides = new ArrayList<Guide>();
    /** Clickable areas of the last frame, so drawing and hit testing always share one layout. */
    private final List<Hit> hits = new ArrayList<Hit>();
    private HudManager.HudElement selected;
    private HudManager.HudElement dragged;
    private boolean resizing;
    private int dragOffsetX, dragOffsetY;
    private int resizeMouseX, resizeMouseY, resizeStartWidth, resizeStartHeight;
    private float resizeStartScale;
    private Pointer drag;
    private int left, top, panelWidth, panelHeight, innerLeft, innerWidth;
    private int bodyTop, areaTop, contentBottom, statusTop, statusHeight;
    private int listWidth, rowHeight, stageLeft, stageRight, stageBottom, inspectorLeft, inspectorWidth;
    private int canvasX, canvasY, canvasWidth, canvasHeight;
    private boolean compact, controlsInStage;
    private float previewScale = 1.0F;
    private int settingsScroll, elementsScroll;
    private int hitTop = Integer.MIN_VALUE, hitBottom = Integer.MAX_VALUE;
    private ModeSetting openMode;
    private StringSetting editingText;
    private String editBuffer = "";
    private ColorSetting editingColor;
    private float colorHue, colorSaturation, colorBrightness;
    private long savedAt;
    /** The HUD at the game's own resolution; the canvas shows it scaled down. */
    private Framebuffer preview;
    private boolean previewUnavailable;

    public HudEditorGui(HudManager manager) {
        this.manager = manager;
        for (String id : manager.getElementIds()) if (manager.isEnabled(id)) { selected = manager.getElement(id); break; }
        if (selected == null) selected = manager.getElement(HudManager.WATERMARK);
    }

    @Override public void initGui() {
        layout();
        buttonList.clear();
        buttonList.add(new AccountScreenStyle.Button(0, left + panelWidth - 62, top + (compact ? 10 : 15), 48, 24, "Close", "", false, false));
    }

    private void layout() {
        panelWidth = Math.min(1200, width - 16);
        panelHeight = Math.min(700, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelWidth < 600 || panelHeight < 330;
        innerLeft = left + 14;
        innerWidth = panelWidth - 28;
        bodyTop = top + (compact ? 44 : 60);
        areaTop = bodyTop + LABEL;
        statusHeight = compact ? 22 : 27;
        statusTop = top + panelHeight - statusHeight - 10;
        contentBottom = statusTop - (compact ? 8 : 10);
        rowHeight = compact ? 22 : 26;
        int gap = compact ? 8 : 12;
        listWidth = Math.max(116, Math.min(170, Math.round(innerWidth * .19F)));
        inspectorWidth = Math.max(168, Math.min(250, Math.round(innerWidth * .28F)));
        int stageWidth = innerWidth - listWidth - inspectorWidth - 2 * gap;
        if (stageWidth < 80) {
            int missing = 80 - stageWidth;
            listWidth = Math.max(80, listWidth - missing / 3);
            inspectorWidth = Math.max(120, inspectorWidth - (missing - missing / 3));
        }
        inspectorLeft = innerLeft + innerWidth - inspectorWidth;
        stageLeft = innerLeft + listWidth + gap;
        stageRight = inspectorLeft - gap;
        // Scale and theme sit under a roomy preview; small windows keep them with the settings.
        controlsInStage = stageRight - stageLeft >= 300;
        stageBottom = controlsInStage ? contentBottom - CONTROL - GAP * 2 : contentBottom;

        ScaledResolution source = new ScaledResolution(mc);
        int availableWidth = Math.max(10, stageRight - stageLeft - 16), availableHeight = Math.max(10, stageBottom - areaTop - 16);
        previewScale = Math.max(.02F, Math.min(availableWidth / (float) Math.max(1, source.getScaledWidth()),
                availableHeight / (float) Math.max(1, source.getScaledHeight())));
        canvasWidth = Math.max(1, Math.round(source.getScaledWidth() * previewScale));
        canvasHeight = Math.max(1, Math.round(source.getScaledHeight() * previewScale));
        canvasX = stageLeft + (stageRight - stageLeft - canvasWidth) / 2;
        canvasY = areaTop + (stageBottom - areaTop - canvasHeight) / 2;
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        layout();
        if (dragged != null) moveSelected(mouseX, mouseY);
        if (drag != null) drag.at(mouseX, mouseY);
        hits.clear();
        KawaseBlur.drawBackdrop(width, height, 8, partialTicks);
        drawRect(0, 0, width, height, 0x70000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title("HUD Editor", innerLeft, top + (compact ? 10 : 14));
        AccountScreenStyle.rawText(AccountScreenStyle.fit("Arrange your in-game overlay", panelWidth - 110), innerLeft,
                top + (compact ? 27 : 33), MUTED);
        drawElements(mouseX, mouseY);
        drawStage(mouseX, mouseY);
        drawInspector(mouseX, mouseY);
        drawStatus();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawElements(int mouseX, int mouseY) {
        String[] ids = manager.getElementIds();
        int shown = 0;
        for (String id : ids) if (manager.isEnabled(id)) shown++;
        String count = shown + "/" + ids.length;
        int countWidth = fontRendererObj.getStringWidth(count);
        label("HUD ELEMENTS", innerLeft, listWidth - countWidth - 6);
        AccountScreenStyle.rawText(count, innerLeft + listWidth - countWidth, bodyTop + 2, MUTED);
        int max = maxElementScroll();
        elementsScroll = Math.max(0, Math.min(elementsScroll, max));
        int right = innerLeft + listWidth - (max > 0 ? 6 : 0);
        hitTop = areaTop;
        hitBottom = contentBottom;
        try (GuiClip ignored = new GuiClip(innerLeft, areaTop, listWidth, contentBottom - areaTop)) {
            int y = areaTop - elementsScroll;
            for (String id : ids) {
                if (y + rowHeight > areaTop && y < contentBottom) drawElementRow(id, right, y, mouseX, mouseY);
                y += rowHeight + GAP;
            }
        }
        hitTop = Integer.MIN_VALUE;
        hitBottom = Integer.MAX_VALUE;
        scrollbar(innerLeft + listWidth - 3, areaTop, contentBottom, elementsScroll, max);
    }

    private void drawElementRow(final String id, int right, int y, int mouseX, int mouseY) {
        final HudManager.HudElement element = manager.getElement(id);
        boolean active = element == selected, enabled = manager.isEnabled(id);
        boolean hover = hovered(innerLeft, y, right, y + rowHeight, mouseX, mouseY);
        AccountScreenStyle.panel(innerLeft, y, right - innerLeft, rowHeight, active ? TINT : hover ? HOVER : SURFACE, active ? ACCENT : BORDER);
        int switchX = right - 28, padding = compact ? 8 : 10;
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(friendlyName(id), switchX - innerLeft - padding - 4), innerLeft + padding,
                y + (rowHeight - 8) / 2, enabled ? TEXT : MUTED);
        toggle(switchX, y + (rowHeight - 12) / 2, enabled);
        addHit(innerLeft, y, right, y + rowHeight, (x, yy) -> select(element));
        addHit(switchX - 4, y, right, y + rowHeight, (x, yy) -> { select(element); toggleElement(id); });
    }

    private void drawStage(int mouseX, int mouseY) {
        ScaledResolution screen = new ScaledResolution(mc);
        String size = screen.getScaledWidth() + " × " + screen.getScaledHeight();
        String title = LanguageManager.translate("LIVE PREVIEW");
        int sizeWidth = fontRendererObj.getStringWidth(size);
        if (fontRendererObj.getStringWidth(title) + sizeWidth + 16 > stageRight - stageLeft) sizeWidth = 0;
        else AccountScreenStyle.rawText(size, stageRight - sizeWidth, bodyTop + 2, MUTED);
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(title, stageRight - stageLeft - sizeWidth - 6), stageLeft, bodyTop + 2, MUTED);
        AccountScreenStyle.panel(stageLeft, areaTop, stageRight - stageLeft, stageBottom - areaTop, SURFACE, BORDER);
        MenuRoundedRenderer.rect(canvasX - 1, canvasY - 1, canvasWidth + 2, canvasHeight + 2, 4, BORDER);
        MenuRoundedRenderer.rect(canvasX, canvasY, canvasWidth, canvasHeight, 3, BACKGROUND);
        try (GuiClip ignored = new GuiClip(canvasX, canvasY, canvasWidth, canvasHeight)) {
            if (!drawBufferedPreview()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(canvasX, canvasY, 0.0F);
                GlStateManager.scale(previewScale, previewScale, 1.0F);
                drawHud();
                GlStateManager.popMatrix();
            }
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            int centre = RenderUtils.alpha(ACCENT, 0x20);
            drawRect(canvasX + canvasWidth / 2, canvasY, canvasX + canvasWidth / 2 + 1, canvasY + canvasHeight, centre);
            drawRect(canvasX, canvasY + canvasHeight / 2, canvasX + canvasWidth, canvasY + canvasHeight / 2 + 1, centre);
            drawGuides();
            HudManager.HudElement hover = dragged == null && drag == null && insideCanvas(mouseX, mouseY)
                    ? elementAt(previewX(mouseX), previewY(mouseY)) : null;
            if (hover != null && hover != selected) {
                int[] bounds = bounds(hover);
                GuiLine.outline(bounds[0] - 1, bounds[1] - 1, bounds[2] + 1, bounds[3] + 1, RenderUtils.alpha(TEXT, 0x70));
            }
            drawSelection();
        }
        addHit(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, this::pressCanvas);
        if (selected != null && manager.isEnabled(selected.getId())) {
            int[] bounds = bounds(selected);
            addHit(bounds[2] - 7, bounds[3] - 7, bounds[2] + 8, bounds[3] + 8, this::pressCanvas);
        }
        if (controlsInStage && selected != null) {
            int controlsTop = contentBottom - CONTROL, middle = stageLeft + (stageRight - stageLeft - GAP) / 2;
            drawScale(stageLeft, middle, controlsTop, mouseX, mouseY);
            drawTheme(middle + GAP, stageRight, controlsTop, mouseX, mouseY);
        }
    }

    private void drawHud() {
        for (String id : manager.getElementIds()) if (manager.isEnabled(id)) manager.drawPreview(manager.getElement(id), fontRendererObj);
    }

    /**
     * Renders the HUD offscreen at the game's own resolution and shows it scaled into the canvas, so thin outlines,
     * glyphs and screen-space clipping look as they do in game. Returns false where framebuffers are unavailable.
     */
    private boolean drawBufferedPreview() {
        if (previewUnavailable || !OpenGlHelper.isFramebufferEnabled()) return false;
        int previous = GL11.glGetInteger(0x8CA6); // GL_FRAMEBUFFER_BINDING
        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            if (preview == null || preview.framebufferWidth != mc.displayWidth || preview.framebufferHeight != mc.displayHeight) {
                if (preview != null) preview.deleteFramebuffer();
                preview = null;
                try {
                    preview = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                } catch (RuntimeException incomplete) {
                    previewUnavailable = true;
                    return false;
                }
                preview.setFramebufferFilter(GL11.GL_LINEAR);
            }
            // An opaque canvas colour keeps the HUD's partial alpha writes out of the composite.
            preview.setFramebufferColor((BACKGROUND >> 16 & 255) / 255.0F, (BACKGROUND >> 8 & 255) / 255.0F, (BACKGROUND & 255) / 255.0F, 1.0F);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            GlStateManager.depthMask(true);
            preview.framebufferClear();
            GlStateManager.depthMask(depthMask);
            preview.bindFramebuffer(true);
            drawHud();
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        float u = preview.framebufferWidth / (float) preview.framebufferTextureWidth;
        float v = preview.framebufferHeight / (float) preview.framebufferTextureHeight;
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        preview.bindFramebufferTexture();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, v); GL11.glVertex2f(canvasX, canvasY);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(canvasX, canvasY + canvasHeight);
        GL11.glTexCoord2f(u, 0.0F); GL11.glVertex2f(canvasX + canvasWidth, canvasY + canvasHeight);
        GL11.glTexCoord2f(u, v); GL11.glVertex2f(canvasX + canvasWidth, canvasY);
        GL11.glEnd();
        preview.unbindFramebufferTexture();
        GlStateManager.enableAlpha();
        return true;
    }

    private void drawGuides() {
        for (Guide guide : guides) {
            if (guide.vertical) {
                int x = canvasX + Math.round(guide.position * previewScale);
                drawRect(x, canvasY, x + 1, canvasY + canvasHeight, ACCENT);
            } else {
                int y = canvasY + Math.round(guide.position * previewScale);
                drawRect(canvasX, y, canvasX + canvasWidth, y + 1, ACCENT);
            }
        }
    }

    private void drawSelection() {
        if (selected == null || !manager.isEnabled(selected.getId())) return;
        int[] bounds = bounds(selected);
        GuiLine.outline(bounds[0] - 1, bounds[1] - 1, bounds[2] + 1, bounds[3] + 1, ACCENT);
        MenuRoundedRenderer.rect(bounds[2] - 5, bounds[3] - 5, 10, 10, 5, BACKGROUND);
        MenuRoundedRenderer.rect(bounds[2] - 4, bounds[3] - 4, 8, 8, 4, ACCENT);
    }

    private void drawInspector(int mouseX, int mouseY) {
        label("INSPECTOR", inspectorLeft, inspectorWidth);
        if (selected == null) {
            AccountScreenStyle.rawText(AccountScreenStyle.fit("Choose an element", inspectorWidth), inspectorLeft, areaTop + 4, MUTED);
            return;
        }
        drawIdentity(inspectorLeft, inspectorLeft + inspectorWidth, areaTop);
        int scrollTop = settingsTop();
        int max = maxSettingsScroll();
        settingsScroll = Math.max(0, Math.min(settingsScroll, max));
        int right = inspectorLeft + inspectorWidth - (max > 0 ? 6 : 0);
        hitTop = scrollTop;
        hitBottom = contentBottom;
        try (GuiClip ignored = new GuiClip(inspectorLeft, scrollTop, inspectorWidth, contentBottom - scrollTop)) {
            int y = scrollTop - settingsScroll;
            if (!controlsInStage) {
                drawScale(inspectorLeft, right, y, mouseX, mouseY);
                y += CONTROL + GAP;
                drawTheme(inspectorLeft, right, y, mouseX, mouseY);
                y += CONTROL + GAP;
            }
            List<Setting<?>> settings = visibleSettings();
            if (settings.isEmpty()) AccountScreenStyle.rawText(AccountScreenStyle.fit("No settings", right - inspectorLeft), inspectorLeft + 2, y + 4, MUTED);
            for (Setting<?> setting : settings) {
                int height = settingHeight(setting);
                if (y + height > scrollTop && y < contentBottom) drawSetting(setting, inspectorLeft, right, y, mouseX, mouseY);
                y += height + GAP;
            }
        }
        hitTop = Integer.MIN_VALUE;
        hitBottom = Integer.MAX_VALUE;
        scrollbar(inspectorLeft + inspectorWidth - 3, scrollTop, contentBottom, settingsScroll, max);
    }

    private void drawIdentity(int x, int right, int y) {
        AccountScreenStyle.panel(x, y, right - x, IDENTITY, SURFACE, BORDER);
        final String id = selected.getId();
        boolean shown = manager.isEnabled(id);
        String state = LanguageManager.translate(shown ? "Visible" : "Hidden");
        int tagWidth = fontRendererObj.getStringWidth(state) + 10, tagX = right - tagWidth - 9, color = shown ? SUCCESS : MUTED;
        MenuRoundedRenderer.rect(tagX, y + 10, tagWidth, 14, 4, RenderUtils.blend(SURFACE, color, .16F));
        AccountScreenStyle.rawText(state, tagX + 5, y + 13, color);
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(friendlyName(id), tagX - x - 16), x + 10, y + 7, TEXT);
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw("X " + selected.getLeft() + "  Y " + selected.getTop(), tagX - x - 16),
                x + 10, y + 20, MUTED);
        addHit(tagX, y + 10, tagX + tagWidth, y + 24, (mouseX, mouseY) -> toggleElement(id));
    }

    private void drawScale(int x, int right, int y, int mouseX, int mouseY) {
        final HudManager.HudElement element = selected;
        AccountScreenStyle.panel(x, y, right - x, CONTROL, SURFACE, BORDER);
        String percent = Math.round(element.getScale() * 100.0F) + "%";
        int percentWidth = fontRendererObj.getStringWidth(percent);
        AccountScreenStyle.rawText(AccountScreenStyle.fit("Scale", right - x - percentWidth - 26), x + 10, y + 8, TEXT);
        AccountScreenStyle.rawText(percent, right - 10 - percentWidth, y + 8, ACCENT);
        int trackY = y + 28;
        stepButton(x + 8, trackY - 6, "-", element, -.05F, mouseX, mouseY);
        stepButton(right - 20, trackY - 6, "+", element, .05F, mouseX, mouseY);
        final int trackLeft = x + 30, trackRight = right - 30;
        int knob = trackLeft + Math.round((trackRight - trackLeft) * (element.getScale() - .5F) / 1.5F);
        track(trackLeft, trackRight, trackY);
        MenuRoundedRenderer.rect(trackLeft, trackY - 1, knob - trackLeft, 3, 1, ACCENT);
        knob(knob, trackY);
        addHit(trackLeft - 5, trackY - 7, trackRight + 5, trackY + 8,
                dragging((mx, my) -> element.setScale(.5F + 1.5F * position(mx, trackLeft, trackRight))));
    }

    private void stepButton(int x, int y, String label, final HudManager.HudElement element, final float step, int mouseX, int mouseY) {
        boolean hover = hovered(x, y, x + 12, y + 12, mouseX, mouseY);
        MenuRoundedRenderer.rect(x, y, 12, 12, 4, hover ? HOVER : TINT);
        AccountScreenStyle.rawText(label, x + (13 - fontRendererObj.getStringWidth(label)) / 2, y + 2, hover ? TEXT : MUTED);
        addHit(x - 2, y - 2, x + 14, y + 14, (mouseX2, mouseY2) -> { element.setScale(element.getScale() + step); persist(); });
    }

    private void drawTheme(int x, int right, int y, int mouseX, int mouseY) {
        final HudManager.HudElement element = selected;
        AccountScreenStyle.panel(x, y, right - x, CONTROL, SURFACE, BORDER);
        AccountScreenStyle.rawText(AccountScreenStyle.fit("Theme", right - x - 20), x + 10, y + 8, TEXT);
        int segmentsLeft = x + 8, segmentsRight = right - 8, segmentsTop = y + 21;
        MenuRoundedRenderer.rect(segmentsLeft, segmentsTop, segmentsRight - segmentsLeft, 14, 7, BACKGROUND);
        String current = effectiveTheme(element);
        for (int i = 0; i < THEMES.length; i++) {
            int segmentLeft = segmentsLeft + (segmentsRight - segmentsLeft) * i / THEMES.length;
            int segmentRight = segmentsLeft + (segmentsRight - segmentsLeft) * (i + 1) / THEMES.length;
            final String theme = THEMES[i];
            boolean active = theme.equalsIgnoreCase(current), hover = hovered(segmentLeft, segmentsTop, segmentRight, segmentsTop + 14, mouseX, mouseY);
            if (active) AccountScreenStyle.panel(segmentLeft, segmentsTop, segmentRight - segmentLeft, 14, TINT, ACCENT);
            String name = AccountScreenStyle.fit("LiquidGlass".equals(theme) ? "Glass" : theme, segmentRight - segmentLeft - 6);
            AccountScreenStyle.rawText(name, (segmentLeft + segmentRight - fontRendererObj.getStringWidth(name)) / 2, segmentsTop + 3,
                    active || hover ? TEXT : MUTED);
            addHit(segmentLeft, segmentsTop, segmentRight, segmentsTop + 14, (mouseX2, mouseY2) -> { element.setTheme(theme); persist(); });
        }
    }

    private void drawSetting(Setting<?> setting, int x, int right, int y, int mouseX, int mouseY) {
        boolean expanded = settingHeight(setting) > headerHeight(setting);
        boolean hover = !expanded && hovered(x, y, right, y + headerHeight(setting), mouseX, mouseY);
        AccountScreenStyle.panel(x, y, right - x, settingHeight(setting), hover ? HOVER : SURFACE, BORDER);
        String name = displayName(setting, selected.getId());
        if (setting instanceof BooleanSetting) {
            final BooleanSetting toggle = (BooleanSetting) setting;
            settingName(name, x, right - 40, y);
            toggle(right - 32, y + 6, toggle.isEnabled());
            addHit(x, y, right, y + ROW, (mx, my) -> { toggle.toggle(); persist(); });
        } else if (setting instanceof NumberSetting) {
            final NumberSetting number = (NumberSetting) setting;
            String value = numberValue(number.getDouble(), number.getIncrement());
            settingName(name, x, rightText(value, right, y, ACCENT) - 8, y);
            final int trackLeft = x + 10, trackRight = right - 10, trackY = y + 26;
            int knob = trackLeft + Math.round((trackRight - trackLeft) * ratio(number.getDouble(), number.getMinimum(), number.getMaximum()));
            track(trackLeft, trackRight, trackY);
            MenuRoundedRenderer.rect(trackLeft, trackY - 1, knob - trackLeft, 3, 1, ACCENT);
            knob(knob, trackY);
            addHit(x, y, right, y + SLIDER_ROW, dragging((mx, my) -> number.setValue(number.getMinimum()
                    + (number.getMaximum() - number.getMinimum()) * position(mx, trackLeft, trackRight))));
        } else if (setting instanceof RangeSetting) {
            final RangeSetting range = (RangeSetting) setting;
            String value = numberValue(range.getMin(), range.getIncrement()) + " - " + numberValue(range.getMax(), range.getIncrement());
            settingName(name, x, rightText(value, right, y, ACCENT) - 8, y);
            final int trackLeft = x + 10, trackRight = right - 10, trackY = y + 26;
            int from = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMin(), range.getMinimum(), range.getMaximum()));
            int to = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMax(), range.getMinimum(), range.getMaximum()));
            track(trackLeft, trackRight, trackY);
            MenuRoundedRenderer.rect(from, trackY - 1, to - from, 3, 1, ACCENT);
            knob(from, trackY);
            knob(to, trackY);
            addHit(x, y, right, y + SLIDER_ROW, (mx, my) -> {
                final RangeSetting.Drag moving = range.beginDrag(rangeValue(range, mx, trackLeft, trackRight));
                drag = (ax, ay) -> moving.move(rangeValue(range, ax, trackLeft, trackRight));
                drag.at(mx, my);
            });
        } else if (setting instanceof ColorSetting) {
            final ColorSetting color = (ColorSetting) setting;
            int swatch = right - 30;
            MenuRoundedRenderer.rect(swatch - 1, y + 5, 22, 14, 4, BORDER);
            RenderUtils.transparencyGrid(swatch, y + 6, swatch + 20, y + 18, 3);
            drawRect(swatch, y + 6, swatch + 20, y + 18, color.getArgb());
            String hex = color.getHex();
            int nameRight = swatch - 8;
            if (fontRendererObj.getStringWidth(name) + fontRendererObj.getStringWidth(hex) + 24 < swatch - x) {
                nameRight = swatch - 8 - fontRendererObj.getStringWidth(hex);
                AccountScreenStyle.rawText(hex, nameRight, y + 8, MUTED);
                nameRight -= 8;
            }
            settingName(name, x, nameRight, y);
            addHit(x, y, right, y + ROW, (mx, my) -> {
                if (editingColor == color) editingColor = null;
                else beginColorEdit(color);
            });
            if (editingColor == color) drawPicker(color, x + 8, right - 8, y + ROW);
        } else if (setting instanceof ModeSetting) {
            final ModeSetting mode = (ModeSetting) setting;
            final boolean open = openMode == mode;
            chevron(right - 17, y + 10, open, MUTED);
            String value = AccountScreenStyle.fit(mode.getValue(), (right - x) / 2);
            settingName(name, x, rightText(value, right - 14, y, ACCENT) - 8, y);
            addHit(x, y, right, y + ROW, (mx, my) -> openMode = open ? null : mode);
            if (open) {
                int optionY = y + ROW;
                for (final String option : mode.getModes()) {
                    boolean active = mode.is(option), optionHover = hovered(x + 4, optionY, right - 4, optionY + OPTION, mouseX, mouseY);
                    if (active || optionHover) MenuRoundedRenderer.rect(x + 4, optionY, right - x - 8, OPTION, 6, active ? TINT : HOVER);
                    if (active) MenuRoundedRenderer.rect(x + 10, optionY + 7, 4, 4, 2, ACCENT);
                    AccountScreenStyle.rawText(AccountScreenStyle.fit(option, right - x - 30), x + 20, optionY + 5, active ? ACCENT : TEXT);
                    addHit(x + 4, optionY, right - 4, optionY + OPTION, (mx, my) -> { mode.setValue(option); openMode = null; persist(); });
                    optionY += OPTION;
                }
            }
        } else if (setting instanceof MultiSelectSetting) {
            final MultiSelectSetting multi = (MultiSelectSetting) setting;
            final boolean open = openMulti.contains(multi);
            chevron(right - 17, y + 10, open, MUTED);
            String value = LanguageManager.format("%s selected", multi.getValue().size());
            settingName(name, x, rightText(value, right - 14, y, MUTED) - 8, y);
            addHit(x, y, right, y + ROW, (mx, my) -> { if (open) openMulti.remove(multi); else openMulti.add(multi); });
            if (open) {
                int optionY = y + ROW;
                for (final String option : multi.getOptions()) {
                    boolean active = multi.isSelected(option);
                    if (hovered(x + 4, optionY, right - 4, optionY + OPTION, mouseX, mouseY))
                        MenuRoundedRenderer.rect(x + 4, optionY, right - x - 8, OPTION, 6, HOVER);
                    MenuRoundedRenderer.rect(x + 10, optionY + 4, 10, 10, 3, active ? ACCENT : BORDER);
                    if (active) check(x + 11, optionY + 6, BACKGROUND);
                    else MenuRoundedRenderer.rect(x + 11, optionY + 5, 8, 8, 2, SURFACE);
                    AccountScreenStyle.rawText(AccountScreenStyle.fit(option, right - x - 36), x + 26, optionY + 5, active ? TEXT : MUTED);
                    addHit(x + 4, optionY, right - 4, optionY + OPTION, (mx, my) -> { multi.toggle(option); persist(); });
                    optionY += OPTION;
                }
            }
        } else if (setting instanceof StringSetting) {
            final StringSetting text = (StringSetting) setting;
            boolean editing = editingText == text;
            settingName(name, x, right - 10, y);
            AccountScreenStyle.panel(x + 8, y + 20, right - x - 16, 16, BACKGROUND, editing ? ACCENT : BORDER);
            String value = editing ? editBuffer + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "") : text.getValue();
            value = fontRendererObj.trimStringToWidth(value, right - x - 32, editing);
            AccountScreenStyle.rawText(value, x + 16, y + 24, editing ? TEXT : MUTED);
            addHit(x, y, right, y + TEXT_ROW, (mx, my) -> { editingText = text; editBuffer = text.getValue(); });
        }
    }

    private void drawPicker(final ColorSetting setting, final int pickerLeft, final int pickerRight, int top) {
        final int svTop = top, svBottom = top + 60, hueTop = svBottom + 6, alphaTop = hueTop + 14;
        int hue = Color.HSBtoRGB(colorHue, 1.0F, 1.0F) | 0xFF000000;
        gradient(pickerLeft, svTop, pickerRight, svBottom, 0xFFFFFFFF, hue, hue, 0xFFFFFFFF);
        gradient(pickerLeft, svTop, pickerRight, svBottom, 0, 0, 0xFF000000, 0xFF000000);
        int markerX = pickerLeft + Math.round(colorSaturation * (pickerRight - pickerLeft - 1));
        int markerY = svTop + Math.round((1.0F - colorBrightness) * (svBottom - svTop - 1));
        GuiLine.outline(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFF000000);
        GuiLine.outline(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) {
            int from = Color.HSBtoRGB(i / 6.0F, 1.0F, 1.0F), to = Color.HSBtoRGB((i + 1) / 6.0F, 1.0F, 1.0F);
            gradient(pickerLeft + (pickerRight - pickerLeft) * i / 6, hueTop, pickerLeft + (pickerRight - pickerLeft) * (i + 1) / 6, hueTop + 8,
                    from, to, to, from);
        }
        sliderHandle(pickerLeft + Math.round(colorHue * (pickerRight - pickerLeft - 1)), hueTop);
        RenderUtils.transparencyGrid(pickerLeft, alphaTop, pickerRight, alphaTop + 8, 4);
        int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness) & 0xFFFFFF;
        gradient(pickerLeft, alphaTop, pickerRight, alphaTop + 8, rgb, rgb | 0xFF000000, rgb | 0xFF000000, rgb);
        sliderHandle(pickerLeft + Math.round(setting.getAlpha() * (pickerRight - pickerLeft - 1) / 255.0F), alphaTop);
        addHit(pickerLeft, svTop, pickerRight, svBottom, dragging((mx, my) -> {
            colorSaturation = position(mx, pickerLeft, pickerRight);
            colorBrightness = 1.0F - position(my, svTop, svBottom);
            applyColor(setting, setting.getAlpha());
        }));
        addHit(pickerLeft, hueTop - 3, pickerRight, hueTop + 11, dragging((mx, my) -> {
            colorHue = position(mx, pickerLeft, pickerRight);
            applyColor(setting, setting.getAlpha());
        }));
        addHit(pickerLeft, alphaTop - 3, pickerRight, alphaTop + 11,
                dragging((mx, my) -> applyColor(setting, Math.round(position(mx, pickerLeft, pickerRight) * 255.0F))));
    }

    private void drawStatus() {
        AccountScreenStyle.panel(innerLeft, statusTop, innerWidth, statusHeight, SURFACE, BORDER);
        boolean saved = System.currentTimeMillis() - savedAt < 2000L;
        int middle = statusTop + statusHeight / 2;
        RenderUtils.roundedRect(innerLeft + 9, middle - 2, innerLeft + 13, middle + 2, 2, saved ? SUCCESS : ACCENT);
        String state = saved ? LanguageManager.translate("Saved") : "";
        int stateWidth = saved ? fontRendererObj.getStringWidth(state) + 16 : 0;
        AccountScreenStyle.rawText(AccountScreenStyle.fit("Drag elements • ESC to save and return", innerWidth - 32 - stateWidth),
                innerLeft + 20, middle - 4, MUTED);
        if (saved) AccountScreenStyle.rawText(state, innerLeft + innerWidth - 12 - fontRendererObj.getStringWidth(state), middle - 4, SUCCESS);
    }

    private void label(String key, int x, int maxWidth) {
        AccountScreenStyle.rawText(AccountScreenStyle.fit(key, maxWidth), x, bodyTop + 2, MUTED);
    }

    private void settingName(String name, int x, int right, int y) {
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(name, right - x - 10), x + 10, y + 8, TEXT);
    }

    /** Draws a right-aligned value and returns its left edge. */
    private int rightText(String value, int right, int y, int color) {
        int x = right - 10 - fontRendererObj.getStringWidth(value);
        AccountScreenStyle.rawText(value, x, y + 8, color);
        return x;
    }

    private static void toggle(int x, int y, boolean on) {
        MenuRoundedRenderer.rect(x, y, 22, 12, 6, on ? ACCENT : RenderUtils.blend(SURFACE, MUTED, .28F));
        MenuRoundedRenderer.rect(on ? x + 11 : x + 1, y + 1, 10, 10, 5, on ? BACKGROUND : MUTED);
    }

    private static void track(int left, int right, int y) {
        MenuRoundedRenderer.rect(left, y - 1, right - left, 3, 1, RenderUtils.blend(SURFACE, MUTED, .28F));
    }

    private static void knob(int x, int y) {
        MenuRoundedRenderer.rect(x - 4, y - 4, 9, 9, 4, TEXT);
    }

    private static void sliderHandle(int x, int top) {
        Gui.drawRect(x - 2, top - 2, x + 3, top + 10, 0xFF000000);
        Gui.drawRect(x - 1, top - 1, x + 2, top + 9, 0xFFFFFFFF);
    }

    private static void chevron(int x, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? 2 - i : i;
            Gui.drawRect(x + i, y + row, x + 7 - i, y + row + 1, color);
        }
    }

    private static void check(int x, int y, int color) {
        int[] rows = {2, 3, 4, 3, 2, 1, 0};
        for (int i = 0; i < rows.length; i++) Gui.drawRect(x + 1 + i, y + rows[i], x + 2 + i, y + rows[i] + 2, color);
    }

    private static void scrollbar(int x, int top, int bottom, int scroll, int max) {
        if (max <= 0 || bottom <= top) return;
        int track = bottom - top, thumb = Math.max(12, track * track / (track + max));
        int y = top + (track - thumb) * scroll / max;
        Gui.drawRect(x, top, x + 3, bottom, BORDER);
        RenderUtils.roundedRect(x, y, x + 3, y + thumb, 1, ACCENT);
    }

    private static void gradient(int left, int top, int right, int bottom, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        vertex(left, bottom, bottomLeft);
        vertex(right, bottom, bottomRight);
        vertex(right, top, topRight);
        vertex(left, top, topLeft);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static void vertex(int x, int y, int color) {
        GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color, (byte) (color >>> 24));
        GL11.glVertex2f(x, y);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) close();
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (editingText != null) commitText();
        if (mouseButton == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit(hit.left, hit.top, hit.right, hit.bottom, mouseX, mouseY)) {
                    hit.action.at(mouseX, mouseY);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void pressCanvas(int mouseX, int mouseY) {
        if (selected != null && manager.isEnabled(selected.getId()) && overResizeHandle(mouseX, mouseY)) {
            dragged = selected;
            resizing = true;
            resizeMouseX = mouseX;
            resizeMouseY = mouseY;
            resizeStartScale = selected.getScale();
            resizeStartWidth = selected.getWidth();
            resizeStartHeight = selected.getHeight();
            return;
        }
        if (!insideCanvas(mouseX, mouseY)) return;
        int x = previewX(mouseX), y = previewY(mouseY);
        HudManager.HudElement target = selected != null && manager.isEnabled(selected.getId()) && isInside(selected, x, y) ? selected : elementAt(x, y);
        if (target == null) return;
        select(target);
        dragged = target;
        resizing = false;
        dragOffsetX = x - target.getLeft();
        dragOffsetY = y - target.getTop();
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            if (dragged != null || drag != null) persist();
            dragged = null;
            resizing = false;
            drag = null;
            guides.clear();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        int step = wheel > 0 ? -1 : 1;
        if (selected != null && insideCanvas(mouseX, mouseY)) {
            selected.setScale(selected.getScale() - step * .05F);
            persist();
        } else if (mouseX >= inspectorLeft && mouseX < inspectorLeft + inspectorWidth) {
            settingsScroll = Math.max(0, Math.min(maxSettingsScroll(), settingsScroll + step * 28));
        } else if (mouseX >= innerLeft && mouseX < innerLeft + listWidth) {
            elementsScroll = Math.max(0, Math.min(maxElementScroll(), elementsScroll + step * (rowHeight + GAP)));
        }
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (editingText != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { commitText(); return; }
            if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            else if (typedChar >= 32 && typedChar <= 126 && editBuffer.length() < editingText.getMaxLength()) editBuffer += typedChar;
            editingText.setValue(editBuffer);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE && (openMode != null || editingColor != null)) {
            openMode = null;
            editingColor = null;
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_H) { close(); return; }
        super.keyTyped(typedChar, keyCode);
    }

    @Override public boolean doesGuiPauseGame() { return false; }

    @Override public void onGuiClosed() {
        if (preview != null) preview.deleteFramebuffer();
        preview = null;
        super.onGuiClosed();
    }

    private void close() {
        if (editingText != null) commitText();
        manager.save();
        mc.displayGuiScreen(null);
    }

    private void commitText() {
        editingText.setValue(editBuffer);
        editingText = null;
        persist();
    }

    private void select(HudManager.HudElement element) {
        if (element == selected) return;
        selected = element;
        settingsScroll = 0;
        openMode = null;
        editingColor = null;
        openMulti.clear();
    }

    private Pointer dragging(final Pointer pointer) {
        return (mouseX, mouseY) -> { drag = pointer; pointer.at(mouseX, mouseY); };
    }

    private void addHit(int left, int top, int right, int bottom, Pointer action) {
        top = Math.max(top, hitTop);
        bottom = Math.min(bottom, hitBottom);
        if (right > left && bottom > top) hits.add(new Hit(left, top, right, bottom, action));
    }

    private boolean hovered(int left, int top, int right, int bottom, int mouseX, int mouseY) {
        return dragged == null && drag == null && mouseY >= hitTop && mouseY < hitBottom && hit(left, top, right, bottom, mouseX, mouseY);
    }

    private void moveSelected(int mouseX, int mouseY) {
        if (dragged == null) return;
        if (resizing) {
            dragged.setScale(HudResizeMath.fromDrag(resizeStartScale, resizeStartWidth, resizeStartHeight,
                    (mouseX - resizeMouseX) / previewScale, (mouseY - resizeMouseY) / previewScale));
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int[] snapped = snap(dragged, previewX(mouseX) - dragOffsetX, previewY(mouseY) - dragOffsetY, resolution);
        dragged.moveTo(snapped[0], snapped[1], resolution);
    }

    private int[] snap(HudManager.HudElement element, int desiredLeft, int desiredTop, ScaledResolution resolution) {
        final int threshold = 5; guides.clear(); int w = Math.max(1, element.getWidth()), h = Math.max(1, element.getHeight()); int[] vertical = new int[] {0, resolution.getScaledWidth() / 2, resolution.getScaledWidth()}, horizontal = new int[] {0, resolution.getScaledHeight() / 2, resolution.getScaledHeight()}, xPoints = new int[] {desiredLeft, desiredLeft + w / 2, desiredLeft + w}, yPoints = new int[] {desiredTop, desiredTop + h / 2, desiredTop + h}; int bestX = threshold + 1, bestY = threshold + 1, deltaX = 0, deltaY = 0, guideX = 0, guideY = 0;
        for (int target : vertical) for (int point : xPoints) if (Math.abs(target - point) < bestX) { bestX = Math.abs(target - point); deltaX = target - point; guideX = target; }
        for (int target : horizontal) for (int point : yPoints) if (Math.abs(target - point) < bestY) { bestY = Math.abs(target - point); deltaY = target - point; guideY = target; }
        for (String id : manager.getElementIds()) { HudManager.HudElement other = manager.getElement(id); if (other == element || !manager.isEnabled(id)) continue; int[] otherX = new int[] {other.getLeft(), other.getLeft() + other.getWidth() / 2, other.getLeft() + other.getWidth()}, otherY = new int[] {other.getTop(), other.getTop() + other.getHeight() / 2, other.getTop() + other.getHeight()}; for (int target : otherX) for (int point : xPoints) if (Math.abs(target - point) < bestX) { bestX = Math.abs(target - point); deltaX = target - point; guideX = target; } for (int target : otherY) for (int point : yPoints) if (Math.abs(target - point) < bestY) { bestY = Math.abs(target - point); deltaY = target - point; guideY = target; } }
        if (bestX <= threshold) { desiredLeft += deltaX; guides.add(new Guide(true, guideX)); } if (bestY <= threshold) { desiredTop += deltaY; guides.add(new Guide(false, guideY)); } return new int[] {desiredLeft, desiredTop};
    }

    private void applyColor(ColorSetting setting, int alpha) {
        int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness);
        setting.setRgba((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    private void beginColorEdit(ColorSetting setting) { editingColor = setting; float[] hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null); colorHue = hsb[0]; colorSaturation = hsb[1]; colorBrightness = hsb[2]; }
    private static double rangeValue(RangeSetting setting, int mouseX, int trackLeft, int trackRight) { return setting.getMinimum() + (setting.getMaximum() - setting.getMinimum()) * position(mouseX, trackLeft, trackRight); }

    private boolean overResizeHandle(int mouseX, int mouseY) {
        if (selected == null) return false;
        int[] bounds = bounds(selected);
        return hit(bounds[2] - 7, bounds[3] - 7, bounds[2] + 8, bounds[3] + 8, mouseX, mouseY);
    }

    private int[] bounds(HudManager.HudElement element) {
        return new int[] {canvasX + Math.round(element.getLeft() * previewScale), canvasY + Math.round(element.getTop() * previewScale),
                canvasX + Math.round((element.getLeft() + Math.max(1, element.getWidth())) * previewScale),
                canvasY + Math.round((element.getTop() + Math.max(1, element.getHeight())) * previewScale)};
    }

    private HudManager.HudElement elementAt(int x, int y) {
        String[] ids = manager.getElementIds();
        for (int i = ids.length - 1; i >= 0; i--) if (manager.isEnabled(ids[i]) && isInside(manager.getElement(ids[i]), x, y)) return manager.getElement(ids[i]);
        return null;
    }

    private int maxElementScroll() {
        return Math.max(0, manager.getElementIds().length * (rowHeight + GAP) - GAP - (contentBottom - areaTop));
    }

    private int settingsTop() { return areaTop + IDENTITY + GAP * 2; }

    private int maxSettingsScroll() {
        if (selected == null) return 0;
        int content = controlsInStage ? 0 : 2 * (CONTROL + GAP);
        List<Setting<?>> settings = visibleSettings();
        if (settings.isEmpty()) content += 12;
        for (Setting<?> setting : settings) content += settingHeight(setting) + GAP;
        return Math.max(0, content - GAP - (contentBottom - settingsTop()));
    }

    private List<Setting<?>> visibleSettings() {
        List<Setting<?>> visible = new ArrayList<Setting<?>>();
        if (selected != null) for (Setting<?> setting : settingsFor(selected.getId())) if (setting.isVisible()) visible.add(setting);
        return visible;
    }

    private List<Setting<?>> settingsFor(String element) {
        List<Setting<?>> settings = new ArrayList<Setting<?>>(); HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); if (hud == null) return settings;
        if (HudManager.MUSIC.equals(element)) { MusicModule music = Vibe.getInstance().getModuleManager().getModule(MusicModule.class); if (music != null) { settings.add(music.hudWidth); settings.add(music.hudScale); settings.add(music.cover); settings.add(music.coverBackground); settings.add(music.progress); settings.add(music.scroll); settings.add(music.hideIdle); } }
        else if (HudManager.WATERMARK.equals(element)) { settings.add(hud.getWatermarkOutline()); settings.add(hud.getWatermarkDetails()); settings.add(hud.getWatermarkText()); }
        else if (HudManager.ARRAY_LIST.equals(element)) { settings.add(hud.getArrayOutline()); settings.add(hud.getArrayListModules()); settings.add(hud.getArrayPrimaryColor()); settings.add(hud.getArraySecondaryColor()); settings.add(hud.getBackground()); settings.addAll(hud.array.all); }
        else if (HudManager.COORDINATES.equals(element)) settings.add(hud.getCoordinatesOutline());
        else if (HudManager.SCOREBOARD.equals(element)) settings.add(hud.getReplaceScoreboardServer());
        else if (HudManager.ARMOR.equals(element)) settings.add(hud.getArmorDisplayMode());
        else if (HudManager.HEALTH.equals(element)) { settings.add(hud.getHealthMaximumColor()); settings.add(hud.getHealthMinimumColor()); settings.add(hud.getHealthAbsorption()); settings.add(hud.getHealthAbsorptionColor()); settings.add(hud.getHealthHideFull()); }
        if (selected != null && "LiquidGlass".equalsIgnoreCase(effectiveTheme(selected))) {
            settings.add(hud.getLiquidGlassBlur()); settings.add(hud.getLiquidGlassBlurStrength());
            settings.add(hud.getLiquidGlassRefraction()); settings.add(hud.getLiquidGlassOpacity()); settings.add(hud.getLiquidGlassTint());
        }
        return settings;
    }
    private void toggleElement(String id) { if (HudManager.MUSIC.equals(id)) { MusicModule music = Vibe.getInstance().getModuleManager().getModule(MusicModule.class); if (music != null) { boolean enable = !manager.isEnabled(id); music.hud.setEnabled(enable); if (enable && !music.isEnabled()) music.setEnabled(true); } } else { HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); if (hud != null) hud.getHudElements().toggle(id); } persist(); }
    private void persist() { manager.save(); if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); if (hud != null) ArrayListRenderer.applyPreset(hud); savedAt = System.currentTimeMillis(); }
    private static int headerHeight(Setting<?> setting) { return setting instanceof NumberSetting || setting instanceof RangeSetting ? SLIDER_ROW : setting instanceof StringSetting ? TEXT_ROW : ROW; }
    private int settingHeight(Setting<?> setting) {
        if (setting instanceof ModeSetting && openMode == setting) return ROW + ((ModeSetting) setting).getModes().size() * OPTION + 4;
        if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) return ROW + ((MultiSelectSetting) setting).getOptions().size() * OPTION + 4;
        if (setting instanceof ColorSetting && editingColor == setting) return ROW + PICKER;
        return headerHeight(setting);
    }
    private String effectiveTheme(HudManager.HudElement element) { if (element.getTheme() != null) return element.getTheme(); HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); return hud == null ? "Vibe" : hud.getMode().getValue(); }
    private String friendlyName(String id) { String value; if (HudManager.ARRAY_LIST.equals(id)) value = "Array list"; else if (HudManager.SESSION_INFO.equals(id)) value = "Statistics"; else if (HudManager.MOTION_GRAPH.equals(id)) value = "Motion graph"; else if (HudManager.CPS_GRAPH.equals(id)) value = "CPS graph"; else if (HudManager.MUSIC.equals(id)) value = "Music"; else { value = id == null ? "" : id.replace('_', ' '); value = value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1); } return LanguageManager.translate(value); }
    private String displayName(Setting<?> setting, String element) {
        String value = setting.getRawName();
        if (HudManager.ARRAY_LIST.equals(element)) value = value.replaceFirst("(?i)^ArrayList\\s*", "").replaceFirst("(?i)^Array\\s*", "");
        else if (element != null) {
            // The inspector already names the element, so "Watermark Outline" reads as "Outline" when that is translated too.
            String shortName = value.replaceFirst("(?i)^" + java.util.regex.Pattern.quote(element) + "\\s+", "");
            if (!shortName.equals(value) && LanguageManager.hasTranslation(shortName, LanguageManager.selectedLanguage())) value = shortName;
        }
        return LanguageManager.translate(value);
    }
    private boolean insideCanvas(int x, int y) { return hit(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, x, y); }
    private int previewX(int mouseX) { return Math.round((mouseX - canvasX) / previewScale); } private int previewY(int mouseY) { return Math.round((mouseY - canvasY) / previewScale); }
    private boolean isInside(HudManager.HudElement element, int x, int y) { return element != null && x >= element.getLeft() && x <= element.getLeft() + Math.max(1, element.getWidth()) && y >= element.getTop() && y <= element.getTop() + Math.max(1, element.getHeight()); }
    private static boolean hit(int left, int top, int right, int bottom, int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
    private static float position(int mouse, int from, int to) { return clamp((mouse - from) / (float) Math.max(1, to - from)); }
    private static float ratio(double value, double min, double max) { return (float) Math.max(0D, Math.min(1D, (value - min) / Math.max(.000001D, max - min))); } private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); } private static String numberValue(double value, double increment) { return increment >= 1D ? Integer.toString((int) Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value); }
    private interface Pointer { void at(int mouseX, int mouseY); }
    private static final class Hit { final int left, top, right, bottom; final Pointer action; Hit(int left, int top, int right, int bottom, Pointer action) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.action = action; } }
    private static final class Guide { final boolean vertical; final int position; Guide(boolean vertical, int position) { this.vertical = vertical; this.position = position; } }
    private static final class GuiLine { static void outline(int left, int top, int right, int bottom, int color) { Gui.drawRect(left, top, right, top + 1, color); Gui.drawRect(left, bottom - 1, right, bottom, color); Gui.drawRect(left, top, left + 1, bottom, color); Gui.drawRect(right - 1, top, right, bottom, color); } }
}
