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
    private static final int ACCENT = 0xFF79D8FF;
    private static final int PANEL = 0xE8101219;
    private static final int PANEL_SOFT = 0xC8171B25;
    private static final int TEXT = 0xFFF0F3F7;
    private static final int MUTED = 0xFF98A2B3;

    private final HudManager manager;
    private final Set<MultiSelectSetting> openMulti = new HashSet<MultiSelectSetting>();
    private final List<Guide> guides = new ArrayList<Guide>();
    private HudManager.HudElement selected;
    private HudManager.HudElement dragged;
    private boolean resizing;
    private int dragOffsetX, dragOffsetY;
    private int leftPanel, leftPanelRight, canvasX, canvasY, canvasWidth, canvasHeight, inspectorLeft;
    private float previewScale = 1.0F;
    private int settingsScroll;
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
        if (draggingNumber != null) setNumberFromMouse(draggingNumber, mouseX);
        if (draggingRange != null && draggingRangeSetting != null) moveRangeFromMouse(draggingRange, draggingRangeSetting, mouseX);
        KawaseBlur.drawBackdrop(width, height, 8, partialTicks);
        drawRect(0, 0, width, height, 0x6510151E);
        layout(); drawHeader(); drawElementList(mouseX, mouseY); drawPreview(); drawInspector(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void layout() {
        leftPanel = 8; leftPanelRight = Math.min(154, Math.max(118, width / 6));
        inspectorLeft = Math.max(leftPanelRight + 170, width - Math.min(286, Math.max(238, width / 4)) - 8);
        int previewLeft = leftPanelRight + 8, previewRight = inspectorLeft - 8, outerTop = 39, outerBottom = height - 10;
        ScaledResolution source = new ScaledResolution(mc);
        previewScale = Math.max(.18F, Math.min(Math.max(10, previewRight - previewLeft - 12) / (float) Math.max(1, source.getScaledWidth()),
                Math.max(10, outerBottom - outerTop - 12) / (float) Math.max(1, source.getScaledHeight())));
        canvasWidth = Math.max(1, Math.round(source.getScaledWidth() * previewScale));
        canvasHeight = Math.max(1, Math.round(source.getScaledHeight() * previewScale));
        canvasX = previewLeft + Math.max(0, (previewRight - previewLeft - canvasWidth) / 2);
        canvasY = outerTop + Math.max(0, (outerBottom - outerTop - canvasHeight) / 2);
    }

    private void drawHeader() {
        RenderUtils.roundedRect(8, 7, width - 8, 31, 4, 0xE9121620); RenderUtils.roundedOutline(8, 7, width - 8, 31, 4, 1, 0x443D4656);
        fontRendererObj.drawString("HUD editor", 17, 15, TEXT);
        String help = "Drag to position  •  Mouse wheel or corner handle to scale  •  Esc to save";
        fontRendererObj.drawString(help, width - 17 - fontRendererObj.getStringWidth(help), 15, MUTED);
    }

    private void drawElementList(int mouseX, int mouseY) {
        panel(leftPanel, 39, leftPanelRight, height - 10, "Elements"); int y = 66;
        for (String id : manager.getElementIds()) {
            HudManager.HudElement element = manager.getElement(id); boolean active = element == selected, enabled = manager.isEnabled(id);
            boolean hover = hit(leftPanel + 7, y, leftPanelRight - 7, y + 24, mouseX, mouseY);
            if (active || hover) RenderUtils.roundedRect(leftPanel + 7, y, leftPanelRight - 7, y + 24, 3, active ? 0xFF263446 : 0xB01C222E);
            if (active) GuiLine.outline(leftPanel + 7, y, leftPanelRight - 7, y + 24, 0x6679D8FF);
            RenderUtils.roundedRect(leftPanel + 13, y + 7, leftPanel + 23, y + 17, 3, enabled ? ACCENT : 0xFF424B59);
            if (enabled) fontRendererObj.drawString("✓", leftPanel + 15, y + 8, 0xFF081018);
            fontRendererObj.drawString(friendlyName(id), leftPanel + 30, y + 8, enabled ? TEXT : MUTED); y += 26;
        }
        fontRendererObj.drawString("Click an item to edit it", leftPanel + 10, height - 23, MUTED);
    }

    private void drawPreview() {
        int frameLeft = leftPanelRight + 8, frameRight = inspectorLeft - 8;
        RenderUtils.roundedRect(frameLeft, 39, frameRight, height - 10, 5, 0x40111620); RenderUtils.roundedOutline(frameLeft, 39, frameRight, height - 10, 5, 1, 0x4B576775);
        RenderUtils.roundedRect(canvasX - 2, canvasY - 2, canvasX + canvasWidth + 2, canvasY + canvasHeight + 2, 3, 0x181B2635);
        RenderUtils.roundedOutline(canvasX - 2, canvasY - 2, canvasX + canvasWidth + 2, canvasY + canvasHeight + 2, 3, 1, 0x4DFFFFFF);
        try (GuiClip ignored = new GuiClip(canvasX, canvasY, canvasWidth, canvasHeight)) {
            GlStateManager.pushMatrix(); GlStateManager.translate(canvasX, canvasY, 0.0F); GlStateManager.scale(previewScale, previewScale, 1.0F);
            for (String id : manager.getElementIds()) if (manager.isEnabled(id)) manager.drawPreview(manager.getElement(id), fontRendererObj);
            drawGuides(); drawSelection(); GlStateManager.popMatrix();
        }
        String caption = selected == null ? "Select an element" : friendlyName(selected.getId()) + "  •  " + Math.round(selected.getScale() * 100.0F) + "%";
        RenderUtils.roundedRect(canvasX + 8, canvasY + 8, canvasX + 14 + fontRendererObj.getStringWidth(caption), canvasY + 23, 3, 0xB8151921);
        fontRendererObj.drawString(caption, canvasX + 11, canvasY + 12, TEXT);
    }
    private void drawGuides() { ScaledResolution res = new ScaledResolution(mc); for (Guide guide : guides) if (guide.vertical) drawRect(guide.position, 0, guide.position + 1, res.getScaledHeight(), 0x99FFFFFF); else drawRect(0, guide.position, res.getScaledWidth(), guide.position + 1, 0x99FFFFFF); }
    private void drawSelection() {
        if (selected == null || !manager.isEnabled(selected.getId())) return;
        int left = selected.getLeft(), top = selected.getTop(), right = left + Math.max(1, selected.getWidth()), bottom = top + Math.max(1, selected.getHeight());
        GuiLine.outline(left - 1, top - 1, right + 1, bottom + 1, 0xEFFFFFFF);
        RenderUtils.roundedRect(right - 4, bottom - 4, right + 4, bottom + 4, 2, 0xFFFFFFFF); RenderUtils.roundedOutline(right - 4, bottom - 4, right + 4, bottom + 4, 2, 1, 0xB010141A);
    }

    private void drawInspector(int mouseX, int mouseY) {
        panel(inspectorLeft, 39, width - 8, height - 10, "Inspector"); if (selected == null) return;
        int left = inspectorLeft + 8, right = width - 16, y = 66 - settingsScroll;
        fontRendererObj.drawString(friendlyName(selected.getId()), left + 3, y, TEXT);
        String position = selected.getLeft() + ", " + selected.getTop(); fontRendererObj.drawString(position, right - fontRendererObj.getStringWidth(position), y, MUTED); y += 19;
        drawThemeRow(left, right, y); y += 39;
        try (GuiClip ignored = new GuiClip(inspectorLeft + 4, 85, width - inspectorLeft - 16, height - 99)) {
            for (Setting<?> setting : settingsFor(selected.getId())) if (setting.isVisible()) { drawSetting(left, right, y, setting, mouseX, mouseY); y += settingHeight(setting); }
        }
    }
    private void drawThemeRow(int left, int right, int y) {
        RenderUtils.roundedRect(left, y, right, y + 31, 3, 0xC9181D27); fontRendererObj.drawString("Theme", left + 7, y + 11, TEXT);
        int valueLeft = right - 96; RenderUtils.roundedRect(valueLeft, y + 5, right - 5, y + 26, 3, 0xFF273345); fontRendererObj.drawString(effectiveTheme(selected), valueLeft + 7, y + 11, ACCENT); fontRendererObj.drawString("›", right - 14, y + 11, MUTED);
    }
    private void drawSetting(int left, int right, int y, Setting<?> setting, int mouseX, int mouseY) {
        RenderUtils.roundedRect(left, y, right, y + 32, 3, mouseY >= y && mouseY < y + 32 ? 0xD9202632 : PANEL_SOFT);
        fontRendererObj.drawString(displayName(setting, selected.getId()), left + 7, y + 11, TEXT);
        if (setting instanceof BooleanSetting) {
            boolean on = ((BooleanSetting) setting).isEnabled(); RenderUtils.roundedRect(right - 36, y + 9, right - 8, y + 23, 7, on ? ACCENT : 0xFF48515E); RenderUtils.roundedRect(on ? right - 21 : right - 34, y + 11, on ? right - 10 : right - 23, y + 21, 5, 0xFFF5F8FC);
        } else if (setting instanceof ColorSetting) {
            ColorSetting color = (ColorSetting) setting; RenderUtils.transparencyGrid(right - 38, y + 7, right - 8, y + 25, 4); RenderUtils.roundedRect(right - 38, y + 7, right - 8, y + 25, 3, color.getArgb()); GuiLine.outline(right - 38, y + 7, right - 8, y + 25, 0x776E7888);
        } else if (setting instanceof NumberSetting || setting instanceof RangeSetting) drawSlider(left, right, y, setting);
        else if (setting instanceof StringSetting) drawClippedValue(editingText == setting ? editBuffer + "|" : ((StringSetting) setting).getValue(), right - 116, y + 11, right - 8, MUTED);
        else if (setting instanceof ModeSetting) { drawClippedValue(((ModeSetting) setting).getValue(), right - 116, y + 11, right - 17, ACCENT); fontRendererObj.drawString(openMode == setting ? "⌃" : "⌄", right - 13, y + 11, MUTED); }
        else if (setting instanceof MultiSelectSetting) { drawClippedValue(((MultiSelectSetting) setting).getValue().size() + " selected", right - 110, y + 11, right - 17, ACCENT); fontRendererObj.drawString(openMulti.contains(setting) ? "⌃" : "⌄", right - 13, y + 11, MUTED); }
        int nextY = y + 32;
        if (setting instanceof ModeSetting && openMode == setting) for (String option : ((ModeSetting) setting).getModes()) { RenderUtils.roundedRect(left + 5, nextY + 1, right - 5, nextY + 22, 2, ((ModeSetting) setting).is(option) ? 0xFF293A50 : 0xE8171B24); fontRendererObj.drawString(option, left + 11, nextY + 7, ((ModeSetting) setting).is(option) ? ACCENT : TEXT); nextY += 23; }
        else if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) for (String option : ((MultiSelectSetting) setting).getOptions()) { boolean on = ((MultiSelectSetting) setting).isSelected(option); RenderUtils.roundedRect(left + 5, nextY + 1, right - 5, nextY + 22, 2, 0xE8171B24); RenderUtils.roundedRect(left + 10, nextY + 7, left + 20, nextY + 17, 2, on ? ACCENT : 0xFF48515E); if (on) fontRendererObj.drawString("✓", left + 12, nextY + 8, 0xFF081018); fontRendererObj.drawString(option, left + 27, nextY + 7, on ? TEXT : MUTED); nextY += 23; }
        else if (setting instanceof ColorSetting && editingColor == setting) drawColorPicker(left + 5, right - 5, nextY + 5, (ColorSetting) setting);
    }
    private void drawSlider(int left, int right, int y, Setting<?> setting) {
        int trackLeft = left + 8, trackRight = right - 8, trackY = y + 24; drawRect(trackLeft, trackY, trackRight, trackY + 2, 0xFF3C4654);
        if (setting instanceof NumberSetting) { NumberSetting number = (NumberSetting) setting; int knob = trackLeft + Math.round((trackRight - trackLeft) * ratio(number.getDouble(), number.getMinimum(), number.getMaximum())); drawRect(trackLeft, trackY, knob, trackY + 2, ACCENT); RenderUtils.roundedRect(knob - 3, trackY - 3, knob + 3, trackY + 5, 3, ACCENT); String value = numberValue(number.getDouble(), number.getIncrement()); fontRendererObj.drawString(value, trackRight - fontRendererObj.getStringWidth(value), y + 11, ACCENT); }
        else { RangeSetting range = (RangeSetting) setting; int from = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMin(), range.getMinimum(), range.getMaximum())), to = trackLeft + Math.round((trackRight - trackLeft) * ratio(range.getMax(), range.getMinimum(), range.getMaximum())); drawRect(from, trackY, to, trackY + 2, ACCENT); RenderUtils.roundedRect(from - 3, trackY - 3, from + 3, trackY + 5, 3, ACCENT); RenderUtils.roundedRect(to - 3, trackY - 3, to + 3, trackY + 5, 3, ACCENT); String value = numberValue(range.getMin(), range.getIncrement()) + " – " + numberValue(range.getMax(), range.getIncrement()); fontRendererObj.drawString(value, trackRight - fontRendererObj.getStringWidth(value), y + 11, ACCENT); }
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
        if (mouseButton == 0 && mouseY >= 66 && mouseX >= leftPanel + 7 && mouseX <= leftPanelRight - 7) { int index = (mouseY - 66) / 26; String[] ids = manager.getElementIds(); if (index >= 0 && index < ids.length && mouseY < 66 + (index + 1) * 26) { selected = manager.getElement(ids[index]); if (mouseX <= leftPanel + 27) toggleElement(ids[index]); return; } }
        if (mouseButton == 0 && selected != null && hit(inspectorLeft + 8, 85 - settingsScroll, width - 16, 116 - settingsScroll, mouseX, mouseY)) { selected.setTheme(nextTheme(effectiveTheme(selected))); persist(); return; }
        if (mouseButton == 0 && handleInspectorClick(mouseX, mouseY)) return;
        if (selected != null && mouseButton == 0 && insideCanvas(mouseX, mouseY)) {
            int x = previewX(mouseX), y = previewY(mouseY);
            if (!isInside(selected, x, y)) for (int i = manager.getElementIds().length - 1; i >= 0; i--) { String id = manager.getElementIds()[i]; if (manager.isEnabled(id) && isInside(manager.getElement(id), x, y)) { selected = manager.getElement(id); break; } }
            if (isInside(selected, x, y)) { int right = selected.getLeft() + selected.getWidth(), bottom = selected.getTop() + selected.getHeight(); resizing = x >= right - 8 && y >= bottom - 8; dragged = selected; dragOffsetX = x - selected.getLeft(); dragOffsetY = y - selected.getTop(); return; }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    private boolean handleInspectorClick(int mouseX, int mouseY) {
        if (selected == null || mouseX < inspectorLeft + 8 || mouseX > width - 16) return false; int left = inspectorLeft + 8, right = width - 16, y = 124 - settingsScroll;
        for (Setting<?> setting : settingsFor(selected.getId())) { if (!setting.isVisible()) continue;
            if (hit(left, y, right, y + 32, mouseX, mouseY)) {
                if (setting instanceof BooleanSetting) { ((BooleanSetting) setting).toggle(); persist(); return true; }
                if (setting instanceof NumberSetting) { draggingNumber = (NumberSetting) setting; setNumberFromMouse(draggingNumber, mouseX); return true; }
                if (setting instanceof RangeSetting) { draggingRangeSetting = (RangeSetting) setting; draggingRange = draggingRangeSetting.beginDrag(rangeMouseValue(draggingRangeSetting, mouseX)); moveRangeFromMouse(draggingRange, draggingRangeSetting, mouseX); return true; }
                if (setting instanceof StringSetting) { editingText = (StringSetting) setting; editBuffer = editingText.getValue(); return true; }
                if (setting instanceof ColorSetting) { if (editingColor == setting) editingColor = null; else beginColorEdit((ColorSetting) setting); return true; }
                if (setting instanceof ModeSetting) { openMode = openMode == setting ? null : (ModeSetting) setting; return true; }
                if (setting instanceof MultiSelectSetting) { if (!openMulti.add((MultiSelectSetting) setting)) openMulti.remove(setting); return true; }
            }
            int childY = y + 32;
            if (setting instanceof ModeSetting && openMode == setting) for (String option : ((ModeSetting) setting).getModes()) { if (hit(left + 5, childY + 1, right - 5, childY + 22, mouseX, mouseY)) { ((ModeSetting) setting).setValue(option); openMode = null; persist(); return true; } childY += 23; }
            else if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) for (String option : ((MultiSelectSetting) setting).getOptions()) { if (hit(left + 5, childY + 1, right - 5, childY + 22, mouseX, mouseY)) { ((MultiSelectSetting) setting).toggle(option); persist(); return true; } childY += 23; }
            else if (setting instanceof ColorSetting && editingColor == setting && updateColorFromMouse((ColorSetting) setting, left + 5, right - 5, childY + 5, mouseX, mouseY)) return true;
            y += settingHeight(setting);
        }
        return false;
    }
    @Override protected void mouseReleased(int mouseX, int mouseY, int state) { if (state == 0) { if (dragged != null || draggingNumber != null || draggingRange != null) persist(); dragged = null; resizing = false; draggingNumber = null; draggingRange = null; draggingRangeSetting = null; } super.mouseReleased(mouseX, mouseY, state); }
    @Override public void handleMouseInput() throws IOException { super.handleMouseInput(); int wheel = Mouse.getEventDWheel(); if (wheel == 0) return; int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth), mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1; if (selected != null && insideCanvas(mouseX, mouseY)) { selected.setScale(selected.getScale() + (wheel > 0 ? .05F : -.05F)); persist(); } else if (mouseX >= inspectorLeft) settingsScroll = Math.max(0, settingsScroll - (wheel > 0 ? 28 : -28)); }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (editingText != null) { if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) { editingText.setValue(editBuffer); editingText = null; persist(); return; } if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1); else if (typedChar >= 32 && typedChar <= 126 && editBuffer.length() < editingText.getMaxLength()) editBuffer += typedChar; editingText.setValue(editBuffer); return; } if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_H) { manager.save(); mc.displayGuiScreen(null); return; } super.keyTyped(typedChar, keyCode); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private void moveSelected(int mouseX, int mouseY) { if (dragged == null) return; if (resizing) { int virtualX = previewX(mouseX), virtualY = previewY(mouseY); float widthScale = (virtualX - dragged.getLeft()) / (float) Math.max(1, dragged.getWidth()), heightScale = (virtualY - dragged.getTop()) / (float) Math.max(1, dragged.getHeight()); dragged.setScale(Math.max(.50F, Math.min(2.0F, dragged.getScale() * Math.max(widthScale, heightScale)))); return; } ScaledResolution resolution = new ScaledResolution(mc); int[] snapped = snap(dragged, previewX(mouseX) - dragOffsetX, previewY(mouseY) - dragOffsetY, resolution); dragged.moveTo(snapped[0], snapped[1], resolution); }
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
    private int settingHeight(Setting<?> setting) { if (setting instanceof ModeSetting && openMode == setting) return 32 + ((ModeSetting) setting).getModes().size() * 23; if (setting instanceof MultiSelectSetting && openMulti.contains(setting)) return 32 + ((MultiSelectSetting) setting).getOptions().size() * 23; return setting instanceof ColorSetting && editingColor == setting ? 180 : 36; }
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
