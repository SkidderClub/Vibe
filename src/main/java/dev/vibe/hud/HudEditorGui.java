package dev.vibe.hud;

import dev.vibe.Vibe;
import dev.vibe.module.impl.HudModule;
import dev.vibe.module.impl.MusicModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import dev.vibe.ui.ArrayListRenderer;
import dev.vibe.ui.GuiClip;
import dev.vibe.ui.KawaseBlur;
import dev.vibe.ui.RenderUtils;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** A quiet HUD workspace: elements, live preview, then their settings. */
public final class HudEditorGui extends GuiScreen {
    private static final int ACCENT = 0xFF83DDF0;
    private static final int PANEL = 0xF0141822;
    private static final int PANEL_SOFT = 0xE0202632;
    private static final int TEXT = 0xFFF0F3F7;
    private static final int MUTED = 0xFFA0AABB;
    private static final int LIST_TOP = 77;
    private static final int LIST_ROW = 34;
    private static final int SETTINGS_TOP = 184;

    private final HudManager manager;
    private final Set<MultiSelectSetting> openMulti = new HashSet<MultiSelectSetting>();
    private final List<Guide> guides = new ArrayList<Guide>();
    private HudManager.HudElement selected;
    private HudManager.HudElement dragged;
    private boolean resizing;
    private int dragOffsetX, dragOffsetY;
    private int resizeMouseX, resizeMouseY, resizeStartWidth, resizeStartHeight;
    private float resizeStartScale;
    private boolean draggingScale;
    private int leftPanel, leftPanelRight, canvasX, canvasY, canvasWidth, canvasHeight, inspectorLeft;
    private float previewScale = 1.0F;
    private int settingsScroll, elementsScroll;
    private NumberSetting draggingNumber;
    private RangeSetting.Drag draggingRange;
    private RangeSetting draggingRangeSetting;
    private ModeSetting openMode;
    private StringSetting editingText;
    private String editBuffer = "";
    private ColorSetting editingColor;
    private float colorHue, colorSaturation, colorBrightness;

    public HudEditorGui(HudManager manager) {
        this.manager = manager;
        for (String id : manager.getElementIds()) if (manager.isEnabled(id)) { selected = manager.getElement(id); break; }
        if (selected == null) selected = manager.getElement(HudManager.WATERMARK);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (dragged != null) moveSelected(mouseX, mouseY);
        if (draggingScale && selected != null) setScaleFromMouse(mouseX);
        if (draggingNumber != null) setNumberFromMouse(draggingNumber, mouseX);
        if (draggingRange != null && draggingRangeSetting != null) moveRangeFromMouse(draggingRange, draggingRangeSetting, mouseX);
        KawaseBlur.drawBackdrop(width, height, 8, partialTicks);
        drawRect(0, 0, width, height, 0xA00B1019);
        layout(); drawHeader(); drawPreview(); drawElementList(mouseX, mouseY); drawInspector(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void layout() {
        leftPanel = 8; leftPanelRight = Math.min(158, Math.max(128, width / 5));
        inspectorLeft = Math.max(leftPanelRight + 155, width - Math.min(270, Math.max(218, width / 4)) - 8);
        int previewLeft = leftPanelRight + 9, previewRight = inspectorLeft - 9, outerTop = 76, outerBottom = height - 38;
        ScaledResolution source = new ScaledResolution(mc);
        previewScale = Math.max(.02F, Math.min(Math.max(10, previewRight - previewLeft - 12) / (float) Math.max(1, source.getScaledWidth()),
                Math.max(10, outerBottom - outerTop - 12) / (float) Math.max(1, source.getScaledHeight())));
        canvasWidth = Math.max(1, Math.round(source.getScaledWidth() * previewScale));
        canvasHeight = Math.max(1, Math.round(source.getScaledHeight() * previewScale));
        canvasX = previewLeft + Math.max(0, (previewRight - previewLeft - canvasWidth) / 2);
        canvasY = outerTop + Math.max(0, (outerBottom - outerTop - canvasHeight) / 2);
    }

    private void drawHeader() {
        RenderUtils.roundedRect(8, 7, width - 8, 36, 6, 0xF0171D29);
        RenderUtils.roundedOutline(8, 7, width - 8, 36, 6, 1, 0x66546477);
        RenderUtils.roundedRect(16, 14, 31, 29, 4, ACCENT);
        fontRendererObj.drawString("H", 20, 17, 0xFF111D27);
        fontRendererObj.drawString("HUD MANAGER", 39, 13, TEXT);
        fontRendererObj.drawString("Arrange your in-game overlay", 39, 24, MUTED);
        RenderUtils.roundedRect(width - 77, 12, width - 44, 31, 4, 0xFF294651);
        fontRendererObj.drawString("Save", width - 72, 18, TEXT);
        RenderUtils.roundedRect(width - 38, 12, width - 17, 31, 4, 0xFF28303D);
        fontRendererObj.drawString("×", width - 31, 18, TEXT);
    }

    private void drawElementList(int mouseX, int mouseY) {
        panel(leftPanel, 43, leftPanelRight, height - 10, "HUD ELEMENTS");
        int listBottom = height - 39;
        elementsScroll = Math.min(elementsScroll, maxElementScroll());
        try (GuiClip ignored = new GuiClip(leftPanel + 3, LIST_TOP, leftPanelRight - leftPanel - 6, Math.max(1, listBottom - LIST_TOP))) {
            int y = LIST_TOP - elementsScroll;
            for (String id : manager.getElementIds()) {
                HudManager.HudElement element = manager.getElement(id);
                boolean active = element == selected, enabled = manager.isEnabled(id);
                boolean hover = hit(leftPanel + 7, y, leftPanelRight - 7, y + 31, mouseX, mouseY);
                RenderUtils.roundedRect(leftPanel + 7, y, leftPanelRight - 7, y + 31, 5,
                        active ? 0xFF2A3948 : hover ? 0xD327303D : 0xB01C2430);
                if (active) drawRect(leftPanel + 7, y + 5, leftPanel + 9, y + 26, ACCENT);
                RenderUtils.roundedRect(leftPanel + 14, y + 10, leftPanel + 25, y + 21, 3, enabled ? ACCENT : 0xFF45505F);
                if (enabled) fontRendererObj.drawString("✓", leftPanel + 16, y + 11, 0xFF10202A);
                String name = friendlyName(id);
                while (fontRendererObj.getStringWidth(name) > leftPanelRight - leftPanel - 42 && name.length() > 2) name = name.substring(0, name.length() - 1);
                fontRendererObj.drawString(name, leftPanel + 33, y + 12, enabled ? TEXT : MUTED);
                y += LIST_ROW;
            }
        }
        drawRect(leftPanel + 8, height - 36, leftPanelRight - 8, height - 35, 0x445A6B7E);
        fontRendererObj.drawString("Click to edit", leftPanel + 11, height - 27, MUTED);
    }

    private void drawPreview() {
        int frameLeft = leftPanelRight + 9, frameRight = inspectorLeft - 9;
        RenderUtils.roundedRect(frameLeft, 43, frameRight, height - 10, 6, 0xD8111721);
        RenderUtils.roundedOutline(frameLeft, 43, frameRight, height - 10, 6, 1, 0x66546477);
        fontRendererObj.drawString("LIVE CANVAS", frameLeft + 12, 55, TEXT);
        String size = new ScaledResolution(mc).getScaledWidth() + " × " + new ScaledResolution(mc).getScaledHeight();
        fontRendererObj.drawString(size, frameRight - 12 - fontRendererObj.getStringWidth(size), 55, MUTED);
        RenderUtils.roundedRect(canvasX - 2, canvasY - 2, canvasX + canvasWidth + 2, canvasY + canvasHeight + 2, 4, 0x8A0B1320);
        RenderUtils.roundedOutline(canvasX - 2, canvasY - 2, canvasX + canvasWidth + 2, canvasY + canvasHeight + 2, 4, 1, 0x777D91A5);
        try (GuiClip ignored = new GuiClip(canvasX, canvasY, canvasWidth, canvasHeight)) {
            drawRect(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, 0x30192336);
            GlStateManager.pushMatrix(); GlStateManager.translate(canvasX, canvasY, 0.0F); GlStateManager.scale(previewScale, previewScale, 1.0F);
            for (String id : manager.getElementIds()) if (manager.isEnabled(id)) manager.drawPreview(manager.getElement(id), fontRendererObj);
            drawGuides(); GlStateManager.popMatrix();
            drawSelection();
        }
        String caption = selected == null ? "Select an element" : friendlyName(selected.getId()) + "  •  " + Math.round(selected.getScale() * 100.0F) + "%";
        RenderUtils.roundedRect(frameLeft + 9, height - 33, frameRight - 9, height - 17, 4, 0xB51D2632);
        fontRendererObj.drawString(caption, frameLeft + 14, height - 28, ACCENT);
        String hint = "Drag • corner • wheel";
        if (frameRight - frameLeft > fontRendererObj.getStringWidth(caption) + fontRendererObj.getStringWidth(hint) + 35)
            fontRendererObj.drawString(hint, frameRight - 14 - fontRendererObj.getStringWidth(hint), height - 28, MUTED);
    }
    private void drawGuides() { ScaledResolution res = new ScaledResolution(mc); for (Guide guide : guides) if (guide.vertical) drawRect(guide.position, 0, guide.position + 1, res.getScaledHeight(), 0x99FFFFFF); else drawRect(0, guide.position, res.getScaledWidth(), guide.position + 1, 0x99FFFFFF); }
    private void drawSelection() {
        if (selected == null || !manager.isEnabled(selected.getId())) return;
        int left = canvasX + Math.round(selected.getLeft() * previewScale);
        int top = canvasY + Math.round(selected.getTop() * previewScale);
        int right = canvasX + Math.round((selected.getLeft() + Math.max(1, selected.getWidth())) * previewScale);
        int bottom = canvasY + Math.round((selected.getTop() + Math.max(1, selected.getHeight())) * previewScale);
        GuiLine.outline(left - 2, top - 2, right + 2, bottom + 2, ACCENT);
        RenderUtils.roundedRect(right - 6, bottom - 6, right + 6, bottom + 6, 3, 0xFFEDF9FC);
        RenderUtils.roundedOutline(right - 6, bottom - 6, right + 6, bottom + 6, 3, 1, 0xFF315E6C);
        drawRect(right - 2, bottom + 1, right + 3, bottom + 2, 0xFF315E6C);
        drawRect(right + 1, bottom - 2, right + 2, bottom + 3, 0xFF315E6C);
    }

    private void drawInspector(int mouseX, int mouseY) {
        panel(inspectorLeft, 43, width - 8, height - 10, "INSPECTOR");
        if (selected == null) { fontRendererObj.drawString("Choose an element", inspectorLeft + 13, 77, MUTED); return; }
        int left = inspectorLeft + 9, right = width - 17;
        RenderUtils.roundedRect(left, 78, right, 100, 4, 0xFF263441);
        fontRendererObj.drawString(friendlyName(selected.getId()), left + 7, 85, TEXT);
        String position = selected.getLeft() + ", " + selected.getTop();
        fontRendererObj.drawString(position, right - 7 - fontRendererObj.getStringWidth(position), 85, MUTED);
        fontRendererObj.drawString("SCALE", left + 2, 110, MUTED);
        String percent = Math.round(selected.getScale() * 100.0F) + "%";
        fontRendererObj.drawString(percent, right - fontRendererObj.getStringWidth(percent), 110, ACCENT);
        drawRect(left + 24, 131, right - 24, 134, 0xFF405063);
        int knob = left + 24 + Math.round((right - left - 48) * (selected.getScale() - .5F) / 1.5F);
        drawRect(left + 24, 131, knob, 134, ACCENT);
        RenderUtils.roundedRect(knob - 4, 128, knob + 4, 137, 4, 0xFFEDF9FC);
        fontRendererObj.drawString("−", left + 5, 127, TEXT);
        fontRendererObj.drawString("+", right - 14, 127, TEXT);
        drawThemeRow(left, right, 143);
        drawRect(left, 178, right, 179, 0x445A6B7E);
        fontRendererObj.drawString("ELEMENT SETTINGS", left + 2, 175, MUTED);
        settingsScroll = Math.min(settingsScroll, maxSettingsScroll());
        int y = SETTINGS_TOP - settingsScroll;
        try (GuiClip ignored = new GuiClip(inspectorLeft + 4, SETTINGS_TOP, width - inspectorLeft - 16, Math.max(1, height - SETTINGS_TOP - 22))) {
            for (Setting<?> setting : settingsFor(selected.getId())) if (setting.isVisible()) { drawSetting(left, right, y, setting, mouseX, mouseY); y += settingHeight(setting); }
        }
    }
    private void drawThemeRow(int left, int right, int y) {
        RenderUtils.roundedRect(left, y, right, y + 29, 4, 0xFF222C39); fontRendererObj.drawString("Theme", left + 7, y + 10, TEXT);
        int valueLeft = right - 96; RenderUtils.roundedRect(valueLeft, y + 4, right - 5, y + 25, 3, 0xFF314252); fontRendererObj.drawString(effectiveTheme(selected), valueLeft + 7, y + 10, ACCENT); fontRendererObj.drawString("›", right - 14, y + 10, MUTED);
    }
    private void drawSetting(int left, int right, int y, Setting<?> setting, int mouseX, int mouseY) {
        RenderUtils.roundedRect(left, y, right, y + 42, 4, mouseY >= y && mouseY < y + 42 ? 0xFF2A3543 : PANEL_SOFT);
        drawClippedValue(displayName(setting, selected.getId()), left + 7, y + 7, right - 7, TEXT);
        if (setting instanceof BooleanSetting) {
            boolean on = ((BooleanSetting) setting).isEnabled();
            fontRendererObj.drawString(on ? "Enabled" : "Disabled", left + 8, y + 26, on ? ACCENT : MUTED);
            RenderUtils.roundedRect(right - 36, y + 23, right - 8, y + 37, 7, on ? ACCENT : 0xFF48515E);
            RenderUtils.roundedRect(on ? right - 21 : right - 34, y + 25, on ? right - 10 : right - 23, y + 35, 5, 0xFFF5F8FC);
        } else if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting; fontRendererObj.drawString(color.getHex(), left + 8, y + 26, MUTED);
            RenderUtils.transparencyGrid(right - 38, y + 20, right - 8, y + 37, 4); RenderUtils.roundedRect(right - 38, y + 20, right - 8, y + 37, 3, color.getArgb()); GuiLine.outline(right - 38, y + 20, right - 8, y + 37, 0x776E7888);
        } else if (setting instanceof NumberSetting || setting instanceof RangeSetting) drawSlider(left, right, y, setting);
        else if (setting instanceof StringSetting) drawClippedValue(editingText == setting ? editBuffer + "|" : ((StringSetting) setting).getValue(), left + 8, y + 26, right - 8, MUTED);
        else if (setting instanceof ModeSetting) { drawClippedValue(((ModeSetting) setting).getValue(), left + 8, y + 26, right - 18, ACCENT); fontRendererObj.drawString(openMode == setting ? "⌃" : "⌄", right - 13, y + 26, MUTED); }
        else if (setting instanceof MultiSelectSetting) { drawClippedValue(((MultiSelectSetting) setting).getValue().size() + " selected", left + 8, y + 26, right - 18, ACCENT); fontRendererObj.drawString(openMulti.contains(setting) ? "⌃" : "⌄", right - 13, y + 26, MUTED); }
        int nextY = y + 42;
        if (setting instanceof ModeSetting && openMode == setting) for (String option : ((ModeSetting) setting).getModes()) { RenderUtils.roundedRect(left + 5, nextY + 1, right - 5, nextY + 22, 2, ((ModeSetting) setting).is(option) ? 0xFF293A50 : 0xE8171B24); fontRendererObj.drawString(option, left + 11, nextY + 7, ((ModeSetting) setting).is(option) ? ACCENT : TEXT); nextY += 23; }
        else if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) for (String option : ((MultiSelectSetting) setting).getOptions()) { boolean on = ((MultiSelectSetting) setting).isSelected(option); RenderUtils.roundedRect(left + 5, nextY + 1, right - 5, nextY + 22, 2, 0xE8171B24); RenderUtils.roundedRect(left + 10, nextY + 7, left + 20, nextY + 17, 2, on ? ACCENT : 0xFF48515E); if (on) fontRendererObj.drawString("✓", left + 12, nextY + 8, 0xFF081018); fontRendererObj.drawString(option, left + 27, nextY + 7, on ? TEXT : MUTED); nextY += 23; }
        else if (setting instanceof ColorSetting && editingColor == setting) drawColorPicker(left + 5, right - 5, nextY + 5, (ColorSetting) setting);
    }
    private void drawSlider(int left, int right, int y, Setting<?> setting) {
        int trackLeft = left + 8, trackRight = right - 8, trackY = y + 35; drawRect(trackLeft, trackY, trackRight, trackY + 2, 0xFF3C4654);
        if (setting instanceof NumberSetting) { NumberSetting number = (NumberSetting) setting; int knob = trackLeft + Math.round((trackRight - trackLeft) * ratio(number.getDouble(), number.getMinimum(), number.getMaximum())); drawRect(trackLeft, trackY, knob, trackY + 2, ACCENT); RenderUtils.roundedRect(knob - 3, trackY - 3, knob + 3, trackY + 5, 3, ACCENT); String value = numberValue(number.getDouble(), number.getIncrement()); fontRendererObj.drawString(value, left + 8, y + 21, ACCENT); }
        else { RangeSetting range = (RangeSetting) setting; int from = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMin(), range.getMinimum(), range.getMaximum())), to = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMax(), range.getMinimum(), range.getMaximum())); drawRect(from, trackY, to, trackY + 2, ACCENT); RenderUtils.roundedRect(from - 3, trackY - 3, from + 3, trackY + 5, 3, ACCENT); RenderUtils.roundedRect(to - 3, trackY - 3, to + 3, trackY + 5, 3, ACCENT); String value = numberValue(range.getMin(), range.getIncrement()) + " – " + numberValue(range.getMax(), range.getIncrement()); fontRendererObj.drawString(value, left + 8, y + 21, ACCENT); }
    }
    private void drawColorPicker(int left, int right, int top, ColorSetting setting) {
        int bottom = top + 92; for (int yy = top; yy < bottom; yy++) { float brightness = 1.0F - (yy - top) / 91.0F; for (int xx = left; xx < right; xx++) { float saturation = (xx - left) / (float) Math.max(1, right - left - 1); drawRect(xx, yy, xx + 1, yy + 1, 0xFF000000 | (Color.HSBtoRGB(colorHue, saturation, brightness) & 0xFFFFFF)); } }
        int markerX = left + Math.round(colorSaturation * (right - left - 1)), markerY = top + Math.round((1.0F - colorBrightness) * 91.0F); GuiLine.outline(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
        int hueTop = bottom + 7; for (int xx = left; xx < right; xx++) drawRect(xx, hueTop, xx + 1, hueTop + 7, 0xFF000000 | (Color.HSBtoRGB((xx - left) / (float) Math.max(1, right - left - 1), 1.0F, 1.0F) & 0xFFFFFF));
        int hueX = left + Math.round(colorHue * (right - left - 1)); GuiLine.outline(hueX - 2, hueTop - 2, hueX + 3, hueTop + 9, 0xFFFFFFFF);
        int alphaTop = hueTop + 14; RenderUtils.transparencyGrid(left, alphaTop, right, alphaTop + 7, 4); int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness) & 0xFFFFFF; for (int xx = left; xx < right; xx++) { int alpha = Math.round((xx - left) * 255.0F / Math.max(1, right - left - 1)); drawRect(xx, alphaTop, xx + 1, alphaTop + 7, (alpha << 24) | rgb); }
        int alphaX = left + Math.round(setting.getAlpha() * (right - left - 1) / 255.0F); GuiLine.outline(alphaX - 2, alphaTop - 2, alphaX + 3, alphaTop + 9, 0xFFFFFFFF); String hex = setting.getHex(); fontRendererObj.drawString(hex, right - fontRendererObj.getStringWidth(hex), alphaTop + 16, MUTED);
    }
    private void panel(int left, int top, int right, int bottom, String title) { RenderUtils.roundedRect(left, top, right, bottom, 5, PANEL); RenderUtils.roundedOutline(left, top, right, bottom, 5, 1, 0x4B576775); RenderUtils.roundedRect(left + 1, top + 1, right - 1, top + 29, 4, 0xE31A1E28); drawRect(left + 5, top + 29, right - 5, top + 30, 0x335F6D82); fontRendererObj.drawString(title, left + 10, top + 11, TEXT); }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && hit(width - 77, 12, width - 44, 31, mouseX, mouseY)) { persist(); return; }
        if (mouseButton == 0 && hit(width - 38, 12, width - 17, 31, mouseX, mouseY)) { manager.save(); mc.displayGuiScreen(null); return; }
        if (mouseButton == 0 && mouseY >= LIST_TOP && mouseY < height - 39 && mouseX >= leftPanel + 7 && mouseX <= leftPanelRight - 7) {
            int index = (mouseY - LIST_TOP + elementsScroll) / LIST_ROW;
            String[] ids = manager.getElementIds();
            if (index >= 0 && index < ids.length) {
                int rowY = LIST_TOP + index * LIST_ROW - elementsScroll;
                if (mouseY < rowY + 31) {
                    selected = manager.getElement(ids[index]); settingsScroll = 0;
                    if (mouseX <= leftPanel + 29) toggleElement(ids[index]);
                    return;
                }
            }
        }
        if (selected != null && mouseButton == 0 && hit(inspectorLeft + 9, 143, width - 17, 172, mouseX, mouseY)) { selected.setTheme(nextTheme(effectiveTheme(selected))); persist(); return; }
        if (selected != null && mouseButton == 0 && hit(inspectorLeft + 9, 123, width - 17, 140, mouseX, mouseY)) {
            int left = inspectorLeft + 9, right = width - 17;
            if (mouseX < left + 24) { selected.setScale(selected.getScale() - .05F); persist(); }
            else if (mouseX > right - 24) { selected.setScale(selected.getScale() + .05F); persist(); }
            else { draggingScale = true; setScaleFromMouse(mouseX); }
            return;
        }
        if (mouseButton == 0 && handleInspectorClick(mouseX, mouseY)) return;
        if (selected != null && mouseButton == 0 && insideCanvas(mouseX, mouseY)) {
            if (manager.isEnabled(selected.getId()) && overResizeHandle(mouseX, mouseY)) {
                dragged = selected; resizing = true; resizeMouseX = mouseX; resizeMouseY = mouseY;
                resizeStartScale = selected.getScale(); resizeStartWidth = selected.getWidth(); resizeStartHeight = selected.getHeight();
                return;
            }
            int x = previewX(mouseX), y = previewY(mouseY);
            if (!isInside(selected, x, y)) for (int i = manager.getElementIds().length - 1; i >= 0; i--) { String id = manager.getElementIds()[i]; if (manager.isEnabled(id) && isInside(manager.getElement(id), x, y)) { selected = manager.getElement(id); break; } }
            if (isInside(selected, x, y)) { dragged = selected; resizing = false; dragOffsetX = x - selected.getLeft(); dragOffsetY = y - selected.getTop(); return; }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    private boolean handleInspectorClick(int mouseX, int mouseY) {
        if (selected == null || mouseX < inspectorLeft + 9 || mouseX > width - 17 || mouseY < SETTINGS_TOP || mouseY >= height - 22) return false; int left = inspectorLeft + 9, right = width - 17, y = SETTINGS_TOP - settingsScroll;
        for (Setting<?> setting : settingsFor(selected.getId())) { if (!setting.isVisible()) continue;
            if (hit(left, y, right, y + 42, mouseX, mouseY)) {
                if (setting instanceof BooleanSetting) { ((BooleanSetting) setting).toggle(); persist(); return true; }
                if (setting instanceof NumberSetting) { draggingNumber = (NumberSetting) setting; setNumberFromMouse(draggingNumber, mouseX); return true; }
                if (setting instanceof RangeSetting) { draggingRangeSetting = (RangeSetting) setting; draggingRange = draggingRangeSetting.beginDrag(rangeMouseValue(draggingRangeSetting, mouseX)); moveRangeFromMouse(draggingRange, draggingRangeSetting, mouseX); return true; }
                if (setting instanceof StringSetting) { editingText = (StringSetting) setting; editBuffer = editingText.getValue(); return true; }
                if (setting instanceof ColorSetting) { if (editingColor == setting) editingColor = null; else beginColorEdit((ColorSetting) setting); return true; }
                if (setting instanceof ModeSetting) { openMode = openMode == setting ? null : (ModeSetting) setting; return true; }
                if (setting instanceof MultiSelectSetting) { if (!openMulti.add((MultiSelectSetting) setting)) openMulti.remove(setting); return true; }
            }
            int childY = y + 42;
            if (setting instanceof ModeSetting && openMode == setting) for (String option : ((ModeSetting) setting).getModes()) { if (hit(left + 5, childY + 1, right - 5, childY + 22, mouseX, mouseY)) { ((ModeSetting) setting).setValue(option); openMode = null; persist(); return true; } childY += 23; }
            else if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) for (String option : ((MultiSelectSetting) setting).getOptions()) { if (hit(left + 5, childY + 1, right - 5, childY + 22, mouseX, mouseY)) { ((MultiSelectSetting) setting).toggle(option); persist(); return true; } childY += 23; }
            else if (setting instanceof ColorSetting && editingColor == setting && updateColorFromMouse((ColorSetting) setting, left + 5, right - 5, childY + 5, mouseX, mouseY)) return true;
            y += settingHeight(setting);
        }
        return false;
    }
    @Override protected void mouseReleased(int mouseX, int mouseY, int state) { if (state == 0) { if (dragged != null || draggingScale || draggingNumber != null || draggingRange != null) persist(); dragged = null; resizing = false; draggingScale = false; guides.clear(); draggingNumber = null; draggingRange = null; draggingRangeSetting = null; } super.mouseReleased(mouseX, mouseY, state); }
    @Override public void handleMouseInput() throws IOException { super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel == 0) return; int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth), mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1; if (selected != null && insideCanvas(mouseX, mouseY)) { selected.setScale(selected.getScale() + (wheel > 0 ? .05F : -.05F)); persist(); } else if (mouseX >= inspectorLeft) settingsScroll = Math.max(0, Math.min(maxSettingsScroll(), settingsScroll - (wheel > 0 ? 28 : -28))); else if (mouseX <= leftPanelRight) elementsScroll = Math.max(0, Math.min(maxElementScroll(), elementsScroll - (wheel > 0 ? LIST_ROW : -LIST_ROW))); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (editingText != null) { if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) { editingText.setValue(editBuffer); editingText = null; persist(); return; } if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1); else if (typedChar >= 32 && typedChar <= 126 && editBuffer.length() < editingText.getMaxLength()) editBuffer += typedChar; editingText.setValue(editBuffer); return; } if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_H) { manager.save(); mc.displayGuiScreen(null); return; } super.keyTyped(typedChar, keyCode); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void moveSelected(int mouseX, int mouseY) { if (dragged == null) return; if (resizing) { dragged.setScale(HudResizeMath.fromDrag(resizeStartScale, resizeStartWidth, resizeStartHeight, (mouseX - resizeMouseX) / previewScale, (mouseY - resizeMouseY) / previewScale)); return; } ScaledResolution resolution = new ScaledResolution(mc); int[] snapped = snap(dragged, previewX(mouseX) - dragOffsetX, previewY(mouseY) - dragOffsetY, resolution); dragged.moveTo(snapped[0], snapped[1], resolution); }
    private int[] snap(HudManager.HudElement element, int desiredLeft, int desiredTop, ScaledResolution resolution) {
        final int threshold = 5; guides.clear(); int w = Math.max(1, element.getWidth()), h = Math.max(1, element.getHeight()); int[] vertical = new int[] {0, resolution.getScaledWidth() / 2, resolution.getScaledWidth()}, horizontal = new int[] {0, resolution.getScaledHeight() / 2, resolution.getScaledHeight()}, xPoints = new int[] {desiredLeft, desiredLeft + w / 2, desiredLeft + w}, yPoints = new int[] {desiredTop, desiredTop + h / 2, desiredTop + h}; int bestX = threshold + 1, bestY = threshold + 1, deltaX = 0, deltaY = 0, guideX = 0, guideY = 0;
        for (int target : vertical) for (int point : xPoints) if (Math.abs(target - point) < bestX) { bestX = Math.abs(target - point); deltaX = target - point; guideX = target; }
        for (int target : horizontal) for (int point : yPoints) if (Math.abs(target - point) < bestY) { bestY = Math.abs(target - point); deltaY = target - point; guideY = target; }
        for (String id : manager.getElementIds()) { HudManager.HudElement other = manager.getElement(id); if (other == element || !manager.isEnabled(id)) continue; int[] otherX = new int[] {other.getLeft(), other.getLeft() + other.getWidth() / 2, other.getLeft() + other.getWidth()}, otherY = new int[] {other.getTop(), other.getTop() + other.getHeight() / 2, other.getTop() + other.getHeight()}; for (int target : otherX) for (int point : xPoints) if (Math.abs(target - point) < bestX) { bestX = Math.abs(target - point); deltaX = target - point; guideX = target; } for (int target : otherY) for (int point : yPoints) if (Math.abs(target - point) < bestY) { bestY = Math.abs(target - point); deltaY = target - point; guideY = target; } }
        if (bestX <= threshold) { desiredLeft += deltaX; guides.add(new Guide(true, guideX)); } if (bestY <= threshold) { desiredTop += deltaY; guides.add(new Guide(false, guideY)); } return new int[] {desiredLeft, desiredTop};
    }
    private boolean updateColorFromMouse(ColorSetting setting, int left, int right, int top, int mouseX, int mouseY) { if (hit(left, top, right, top + 92, mouseX, mouseY)) { colorSaturation = clamp((mouseX - left) / (float) Math.max(1, right - left - 1)); colorBrightness = clamp(1.0F - (mouseY - top) / 91.0F); } else if (hit(left, top + 99, right, top + 106, mouseX, mouseY)) colorHue = clamp((mouseX - left) / (float) Math.max(1, right - left - 1)); else if (hit(left, top + 113, right, top + 120, mouseX, mouseY)) { int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness); setting.setRgba((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(clamp((mouseX - left) / (float) Math.max(1, right - left - 1)) * 255.0F)); persist(); return true; } else return false; int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness); setting.setRgba((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, setting.getAlpha()); persist(); return true; }
    private void beginColorEdit(ColorSetting setting) { editingColor = setting; float[] hsb = Color.RGBtoHSB(setting.getRed(), setting.getGreen(), setting.getBlue(), null); colorHue = hsb[0]; colorSaturation = hsb[1]; colorBrightness = hsb[2]; }
    private void setNumberFromMouse(NumberSetting setting, int mouseX) { int left = inspectorLeft + 16, right = width - 24; setting.setValue(setting.getMinimum() + (setting.getMaximum() - setting.getMinimum()) * clamp((mouseX - left) / (float) Math.max(1, right - left))); }
    private double rangeMouseValue(RangeSetting setting, int mouseX) { int left = inspectorLeft + 16, right = width - 24; return setting.getMinimum() + (setting.getMaximum() - setting.getMinimum()) * clamp((mouseX - left) / (float) Math.max(1, right - left)); }
    private void moveRangeFromMouse(RangeSetting.Drag drag, RangeSetting setting, int mouseX) { drag.move(rangeMouseValue(setting, mouseX)); }

    private boolean overResizeHandle(int mouseX, int mouseY) {
        if (selected == null) return false;
        int right = canvasX + Math.round((selected.getLeft() + selected.getWidth()) * previewScale);
        int bottom = canvasY + Math.round((selected.getTop() + selected.getHeight()) * previewScale);
        return hit(right - 8, bottom - 8, right + 9, bottom + 9, mouseX, mouseY);
    }

    private void setScaleFromMouse(int mouseX) {
        if (selected == null) return;
        int left = inspectorLeft + 33, right = width - 41;
        selected.setScale(.5F + 1.5F * clamp((mouseX - left) / (float) Math.max(1, right - left)));
    }

    private int maxElementScroll() {
        return Math.max(0, manager.getElementIds().length * LIST_ROW - Math.max(1, height - 39 - LIST_TOP));
    }

    private int maxSettingsScroll() {
        if (selected == null) return 0;
        int contentHeight = 0;
        for (Setting<?> setting : settingsFor(selected.getId())) if (setting.isVisible()) contentHeight += settingHeight(setting);
        return Math.max(0, contentHeight - Math.max(1, height - SETTINGS_TOP - 22));
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
    private void persist() { manager.save(); if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); if (hud != null) ArrayListRenderer.applyPreset(hud); }
    private int settingHeight(Setting<?> setting) { if (setting instanceof ModeSetting && openMode == setting) return 42 + ((ModeSetting) setting).getModes().size() * 23 + 4; if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) return 42 + ((MultiSelectSetting) setting).getOptions().size() * 23 + 4; return setting instanceof ColorSetting && editingColor == setting ? 190 : 46; }
    private String effectiveTheme(HudManager.HudElement element) { if (element.getTheme() != null) return element.getTheme(); HudModule hud = Vibe.getInstance().getModuleManager().getModule(HudModule.class); return hud == null ? "Vibe" : hud.getMode().getValue(); }
    private String nextTheme(String theme) { return "Vibe".equals(theme) ? "Skeet" : "Skeet".equals(theme) ? "LiquidGlass" : "Vibe"; }
    private String friendlyName(String id) { if (HudManager.ARRAY_LIST.equals(id)) return "Array list"; if (HudManager.SESSION_INFO.equals(id)) return "Statistics"; if (HudManager.MOTION_GRAPH.equals(id)) return "Motion graph"; if (HudManager.CPS_GRAPH.equals(id)) return "CPS graph"; if (HudManager.MUSIC.equals(id)) return "Music"; String value = id == null ? "" : id.replace('_', ' '); return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1); }
    private String displayName(Setting<?> setting, String element) { String value = setting.getName(); return HudManager.ARRAY_LIST.equals(element) ? value.replaceFirst("(?i)^ArrayList\\s*", "").replaceFirst("(?i)^Array\\s*", "") : value; }
    private boolean insideCanvas(int x, int y) { return hit(canvasX, canvasY, canvasX + canvasWidth, canvasY + canvasHeight, x, y); }
    private int previewX(int mouseX) { return Math.round((mouseX - canvasX) / previewScale); } private int previewY(int mouseY) { return Math.round((mouseY - canvasY) / previewScale); }
    private boolean isInside(HudManager.HudElement element, int x, int y) { return element != null && x >= element.getLeft() && x <= element.getLeft() + Math.max(1, element.getWidth()) && y >= element.getTop() && y <= element.getTop() + Math.max(1, element.getHeight()); }
    private void drawClippedValue(String value, int left, int y, int right, int color) { String text = value == null ? "" : value; while (fontRendererObj.getStringWidth(text) > right - left && text.length() > 1) text = text.substring(0, text.length() - 1); fontRendererObj.drawString(text, left, y, color); }
    private static boolean hit(int left, int top, int right, int bottom, int x, int y) { return x >= left && x < right && y >= top && y < bottom; }
    private static float ratio(double value, double min, double max) { return (float) Math.max(0D, Math.min(1D, (value - min) / Math.max(.000001D, max - min))); } private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); } private static String numberValue(double value, double increment) { return increment >= 1D ? Integer.toString((int) Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static final class Guide { final boolean vertical; final int position; Guide(boolean vertical, int position) { this.vertical = vertical; this.position = position; } }
    private static final class GuiLine { static void outline(int left, int top, int right, int bottom, int color) { net.minecraft.client.gui.Gui.drawRect(left, top, right, top + 1, color); net.minecraft.client.gui.Gui.drawRect(left, bottom - 1, right, bottom, color); net.minecraft.client.gui.Gui.drawRect(left, top, left + 1, bottom, color); net.minecraft.client.gui.Gui.drawRect(right - 1, top, right, bottom, color); } }
}
