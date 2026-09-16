package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.module.impl.ClickGuiModule;
import dev.vibe.setting.BooleanSetting;
import dev.vibe.setting.ColorSetting;
import dev.vibe.setting.ModeSetting;
import dev.vibe.setting.MultiSelectSetting;
import dev.vibe.setting.NumberSetting;
import dev.vibe.setting.RangeSetting;
import dev.vibe.setting.Setting;
import dev.vibe.setting.StringSetting;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiButtonFlags;
import imgui.flag.ImGuiHoveredFlags;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.List;
import dev.vibe.language.LanguageManager;

import net.minecraft.client.Minecraft;

/**
 * Immediate-mode Augustus workspace.  This is deliberately isolated from the
 * native Vibe ClickGUI so the other themes keep their exact interaction model.
 */
public final class AugustusImGui {

    // ImGui colors are packed ABGR.
    private static final int TEXT=0xFFF0F0F0, BLUE=0xFFFF9D00, GREEN=0xFF00FF00, RED=0xFF0000FF;
    private static final int GOLD=0xFF19ACE7, BG=0xFF181818, BORDER=0xFF252525;
    private static final Set<Setting<?>> collapsed = new HashSet<Setting<?>>();
    private static Category category = Category.COMBAT;
    private static Module selected;
    private static Module binding;
    private static boolean unavailable;
    private static AugustusBackend backend;
    private static imgui.internal.ImGuiContext context;
    private static boolean initialized;
    private static float windowX, windowY, windowWidth, windowHeight;
    private static boolean maximized, draggingWindow;
    private static float dragOffsetX, dragOffsetY, dragStartX, dragStartY;
    private static final float MIN_WINDOW_WIDTH = 560, MIN_WINDOW_HEIGHT = 300;
    private static boolean resizingWindow;
    private static float resizeMouseX, resizeMouseY, resizeLeft, resizeTop, resizeRight, resizeBottom;

    private AugustusImGui() {
    }

    public static boolean draw() {
        if (unavailable) return false;
        imgui.internal.ImGuiContext previous = null;
        try {
            previous = ImGui.getCurrentContext();
            if (!initialized) {
                context = ImGui.createContext();
                backend = new AugustusBackend(); backend.init(); initialized = true;
            }
            ImGui.setCurrentContext(context);
            backend.newFrame(Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
            ImGui.newFrame();
            drawWorkspace(Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight, Vibe.getInstance().getModuleManager().getModules());
            ImGui.render();
            backend.render(ImGui.getDrawData());
            return true;
        } catch (Exception | LinkageError error) {
            System.err.println("[Vibe] Augustus ImGui unavailable: " + error);
            unavailable = true;
            if (backend != null) backend.close();
            if (context != null) { ImGui.destroyContext(context); context = null; }
            initialized = false;
            return false;
        } finally {
            if (previous != null) ImGui.setCurrentContext(previous);
        }
    }

    public static void handleMouse() {
        if (initialized && !unavailable) {
            imgui.internal.ImGuiContext previous = ImGui.getCurrentContext();
            try { ImGui.setCurrentContext(context); backend.mouse(); }
            finally { ImGui.setCurrentContext(previous); }
        }
    }

    public static boolean handleKey() {
        if (binding != null && org.lwjgl.input.Keyboard.getEventKeyState()) {
            int key = org.lwjgl.input.Keyboard.getEventKey();
            if (key == org.lwjgl.input.Keyboard.KEY_NONE) return true;
            binding.setKey(key == org.lwjgl.input.Keyboard.KEY_ESCAPE ? org.lwjgl.input.Keyboard.KEY_NONE : key);
            binding = null;
            return true;
        }
        if (initialized && !unavailable) {
            imgui.internal.ImGuiContext previous = ImGui.getCurrentContext();
            try { ImGui.setCurrentContext(context); backend.key(); return ImGui.getIO().getWantTextInput(); }
            finally { ImGui.setCurrentContext(previous); }
        }
        return false;
    }

    public static void closed() {
        binding = null;
        draggingWindow = false;
        resizingWindow = false;
        if (initialized && !unavailable) {
            imgui.internal.ImGuiContext previous = ImGui.getCurrentContext();
            try { ImGui.setCurrentContext(context); backend.resetInput(); }
            finally { ImGui.setCurrentContext(previous); }
        }
    }

    // Also used by the offscreen render check with isolated modules.
    static void drawWorkspace(int displayWidth, int displayHeight, List<Module> allModules) {
        ClickGuiModule clickGui = null;
        for (Module module : allModules) if (module instanceof ClickGuiModule) { clickGui = (ClickGuiModule) module; break; }
        float rounding = clickGui == null || clickGui.getAugustusRoundedCorners().isEnabled() ? 8 : 0;
        int backgroundAlpha = clickGui == null ? 200 : clickGui.getAugustusBackgroundAlpha().getInt();
        applyStyle(rounding, backgroundAlpha);
        if (windowWidth == 0) {
            windowWidth = Math.min(1100, displayWidth * .9F);
            windowHeight = Math.min(640, displayHeight * .9F);
            windowX = (displayWidth - windowWidth) / 2;
            windowY = (displayHeight - windowHeight) / 2;
        }
        updateWindowDrag(displayWidth, displayHeight);
        updateWindowResize(displayWidth, displayHeight);
        float width = maximized ? displayWidth : Math.min(windowWidth, displayWidth);
        float height = maximized ? displayHeight : Math.min(windowHeight, displayHeight);
        float originX = maximized ? 0 : Math.round(clamp(windowX, 0, displayWidth - width));
        float originY = maximized ? 0 : Math.round(clamp(windowY, 0, displayHeight - height));
        List<Module> modules = new ArrayList<Module>(allModules);
        Collections.sort(modules, Comparator.comparing(Module::getRawName, String.CASE_INSENSITIVE_ORDER));
        if (selected == null || selected.getCategory() != category || !modules.contains(selected)) selectFirst(modules);
        float sidebar = Math.min(200, Math.max(160, width * .2F)), contentWidth = Math.max(1, width - sidebar);
        float tabsHeight = tabRows(contentWidth - 28) * 30 + 30;
        ImGui.setNextWindowPos(originX, originY, ImGuiCond.Always);
        ImGui.setNextWindowSize(width, height, ImGuiCond.Always);
        int flags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBackground;
        ImGui.begin("ClickGUI###augustus-workspace", flags);
        beginWindowResize(originX, originY, width, height);
        int childFlags = resizingWindow ? ImGuiWindowFlags.NoInputs : 0;
        ImDrawList draw = ImGui.getWindowDrawList();
        // Clip two fills of the same silhouette so the title and body meet without
        // stacking translucent layers or leaving an anti-aliased seam.
        draw.pushClipRect(originX, originY, originX + width, originY + 30, true);
        draw.addRectFilled(originX, originY, originX + width, originY + height,
                RenderUtils.alpha(0xFF222222, backgroundAlpha), rounding);
        draw.popClipRect();
        draw.pushClipRect(originX, originY + 30, originX + width, originY + height, true);
        draw.addRectFilled(originX, originY, originX + width, originY + height,
                RenderUtils.alpha(BG, backgroundAlpha), rounding);
        draw.popClipRect();
        draw.addText(originX + 8, originY + 7, TEXT, "ClickGUI");
        ImGui.setCursorPos(0, 0);
        if (!resizingWindow) ImGui.invisibleButton("title-drag", Math.max(1, width - 68), 30);
        else ImGui.dummy(Math.max(1, width - 68), 30);
        if (ImGui.isItemActivated()) {
            if (ImGui.isMouseDoubleClicked(0)) {
                maximized = !maximized;
                draggingWindow = false;
            } else {
                draggingWindow = true;
                dragStartX = ImGui.getMousePosX(); dragStartY = ImGui.getMousePosY();
                dragOffsetX = dragStartX - originX; dragOffsetY = dragStartY - originY;
            }
        }
        ImGui.setCursorPos(width - 68, 0);
        if (windowButton("maximize")) maximized = !maximized;
        float buttonX = originX + width - 68;
        if (ImGui.isItemHovered()) draw.addRectFilled(buttonX, originY, buttonX + 34, originY + 30, 0xFF383838);
        if (maximized) {
            draw.addLine(buttonX + 14, originY + 9, buttonX + 23, originY + 9, TEXT);
            draw.addLine(buttonX + 23, originY + 9, buttonX + 23, originY + 18, TEXT);
            draw.addLine(buttonX + 14, originY + 9, buttonX + 14, originY + 12, TEXT);
            draw.addLine(buttonX + 20, originY + 18, buttonX + 23, originY + 18, TEXT);
            draw.addRect(buttonX + 11, originY + 12, buttonX + 20, originY + 21, TEXT);
        } else draw.addRect(buttonX + 12, originY + 10, buttonX + 22, originY + 20, TEXT);
        ImGui.setCursorPos(width - 34, 0);
        if (windowButton("close") && Minecraft.getMinecraft() != null) Minecraft.getMinecraft().displayGuiScreen(null);
        if (ImGui.isItemHovered()) draw.addRectFilled(originX + width - 34, originY, originX + width, originY + 30,
                0xFF3030BB, rounding, ImDrawFlags.RoundCornersTopRight);
        draw.addLine(originX + width - 22, originY + 10, originX + width - 12, originY + 20, TEXT);
        draw.addLine(originX + width - 12, originY + 10, originX + width - 22, originY + 20, TEXT);
        draw.addRectFilled(originX + sidebar - 2, originY + 30, originX + sidebar, originY + height, BORDER);
        draw.addRectFilled(originX + sidebar, originY + 30 + tabsHeight - 2, originX + width, originY + 30 + tabsHeight, BORDER);

        ImGui.setCursorPos(sidebar, 30);
        ImGui.beginChild("categories", contentWidth, tabsHeight - 2, false, ImGuiWindowFlags.NoScrollbar | childFlags);
        ImGui.setCursorPos(18, 18);
        float rowStart = ImGui.getCursorScreenPosX(), right = rowStart + contentWidth - 28;
        boolean first = true;
        for (Category value : Category.values()) {
            String label = value.getLabel().toUpperCase(Locale.ROOT);
            float size = textWidth(label);
            if (!first) nextInline(size, rowStart, right, 24);
            float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
            if (textButton("category-" + value.name(), label, TEXT, size, 27)) {
                category = value; selectFirst(modules); binding = null;
            }
            if (category == value) ImGui.getWindowDrawList().addRectFilled(x, y + 19, x + size, y + 21, TEXT);
            first = false;
        }
        ImGui.endChild();

        ImGui.setCursorPos(0, 30);
        ImGui.beginChild("modules-" + category.name(), sidebar - 2, height - 30, false, childFlags);
        ImGui.setCursorPos(12, 10);
        for (Module module : modules) {
            if (module.getCategory() != category) continue;
            ImGui.pushID(module.getId());
            float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
            ImGui.invisibleButton("module", Math.max(1, ImGui.getContentRegionAvailX() - 8), 20,
                    ImGuiButtonFlags.MouseButtonLeft | ImGuiButtonFlags.MouseButtonRight | ImGuiButtonFlags.MouseButtonMiddle);
            if (ImGui.isItemClicked(0) || ImGui.isItemClicked(1)) { selected = module; binding = null; }
            if (ImGui.isItemClicked(0)) module.toggle();
            if (ImGui.isItemClicked(2)) { selected = module; binding = module; }
            if (ImGui.isItemHovered()) {
                ImGui.getWindowDrawList().addRectFilled(x - 4, y - 1, originX + sidebar - 8, y + 20, 0xFF222222);
                ImGui.setTooltip(module.getName() + "\n" + module.getDescription());
            }
            if (selected == module) ImGui.getWindowDrawList().addText(x, y + 2, TEXT, ">");
            ImGui.getWindowDrawList().addText(x + 18, y + 2, module.isEnabled() ? BLUE : TEXT,
                    fitText(module.getName(), ImGui.getItemRectMaxX() - x - 18));
            ImGui.setCursorPosX(12);
            ImGui.popID();
        }
        ImGui.endChild();

        ImGui.setCursorPos(sidebar, 30 + tabsHeight);
        String moduleId = selected == null ? "empty" : selected.getId();
        ImGui.beginChild("settings-" + moduleId, contentWidth, Math.max(1, height - 30 - tabsHeight), false, childFlags);
        ImGui.setCursorPos(10, 10);
        if (selected == null) ImGui.textWrapped(LanguageManager.translate("No module is available in this category."));
        else drawModule();
        ImGui.endChild();
        if (!maximized) {
            ImDrawList overlay = ImGui.getForegroundDrawList();
            overlay.addRect(originX, originY, originX + width - 1, originY + height - 1, BORDER, rounding);
            int grip = resizingWindow ? BLUE : 0xFF666666;
            for (int offset = 4; offset <= 10; offset += 3)
                overlay.addLine(originX + width - offset - 2, originY + height - 3,
                        originX + width - 3, originY + height - offset - 2, grip);
        }
        ImGui.end();
    }

    private static boolean windowButton(String id) {
        if (!resizingWindow) return ImGui.invisibleButton(id, 34, 30);
        ImGui.dummy(34, 30);
        return false;
    }

    private static void beginWindowResize(float x, float y, float width, float height) {
        if (maximized || draggingWindow || resizingWindow || ImGui.isAnyItemActive()
                || !ImGui.isWindowHovered(ImGuiHoveredFlags.RootAndChildWindows)) return;
        float mouseX = ImGui.getMousePosX(), mouseY = ImGui.getMousePosY();
        // Only the visible grip in the bottom-right corner resizes Augustus.
        if (mouseX < x + width - 12 || mouseX >= x + width
                || mouseY < y + height - 12 || mouseY >= y + height) return;
        if (ImGui.isMouseClicked(0)) {
            resizingWindow = true;
            resizeMouseX = mouseX; resizeMouseY = mouseY;
            resizeLeft = x; resizeTop = y; resizeRight = x + width; resizeBottom = y + height;
        }
    }

    private static void updateWindowResize(int displayWidth, int displayHeight) {
        if (!resizingWindow) return;
        if (!ImGui.isMouseDown(0)) { resizingWindow = false; return; }
        float dx = ImGui.getMousePosX() - resizeMouseX, dy = ImGui.getMousePosY() - resizeMouseY;
        float minWidth = Math.min(MIN_WINDOW_WIDTH, displayWidth), minHeight = Math.min(MIN_WINDOW_HEIGHT, displayHeight);
        float right = clamp(resizeRight + dx, resizeLeft + minWidth, displayWidth);
        float bottom = clamp(resizeBottom + dy, resizeTop + minHeight, displayHeight);
        windowX = Math.round(resizeLeft); windowY = Math.round(resizeTop);
        windowWidth = Math.round(right - resizeLeft); windowHeight = Math.round(bottom - resizeTop);
    }

    private static void updateWindowDrag(int displayWidth, int displayHeight) {
        if (!draggingWindow) return;
        if (!ImGui.isMouseDown(0)) { draggingWindow = false; return; }
        float mouseX = ImGui.getMousePosX(), mouseY = ImGui.getMousePosY();
        if (maximized) {
            if (Math.abs(mouseX - dragStartX) + Math.abs(mouseY - dragStartY) < 4) return;
            // Restore under the pointer, as when dragging a maximized Windows title bar.
            dragOffsetX = dragOffsetX / displayWidth * Math.min(windowWidth, displayWidth);
            maximized = false;
        }
        windowX = clamp(mouseX - dragOffsetX, 0, displayWidth - Math.min(windowWidth, displayWidth));
        windowY = clamp(mouseY - dragOffsetY, 0, displayHeight - Math.min(windowHeight, displayHeight));
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static void selectFirst(List<Module> modules) {
        selected = null;
        for (Module module : modules) if (module.getCategory() == category) { selected = module; break; }
    }

    private static int tabRows(float available) {
        int rows = 1;
        float used = 0;
        for (Category value : Category.values()) {
            float size = textWidth(value.getLabel().toUpperCase(Locale.ROOT));
            if (used > 0 && used + 24 + size > available) { rows++; used = 0; }
            used += (used > 0 ? 24 : 0) + size;
        }
        return rows;
    }

    private static void drawModule() {
        ImGui.pushID(selected.getId());
        float left = ImGui.getCursorScreenPosX(), top = ImGui.getCursorScreenPosY();
        float right = left + ImGui.getContentRegionAvailX() - 12;
        String reset = LanguageManager.translate("Reset");
        String title = selected.getName().toUpperCase(Locale.ROOT) + ":";
        ImGui.getWindowDrawList().addText(left, top, BLUE, fitText(title, right - left - textWidth(reset) - 20));
        ImGui.setCursorScreenPos(Math.max(left, right - textWidth(reset)), top);
        if (textButton("reset", reset, TEXT, textWidth(reset), 24)) {
            for (Setting<?> setting : selected.getSettings()) setting.resetToDefault();
        }
        ImGui.setCursorScreenPos(left, top + 24);
        String key = selected.getKey() == 0 ? "NONE" : org.lwjgl.input.Keyboard.getKeyName(selected.getKey());
        String keyLabel = LanguageManager.translate("Key") + ": " + (binding == selected ? "..." : key);
        if (textButton("bind", keyLabel, binding == selected ? GOLD : TEXT, textWidth(keyLabel), 24)) binding = selected;
        if (ImGui.isItemHovered()) ImGui.setTooltip("Press a key to bind. Escape: clear.");
        if (selected.isToggleable()) {
            String enabled = LanguageManager.translate("Enabled") + ":";
            nextInline(textWidth(enabled) + 80, left, right, 40);
            label(enabled);
            if (booleanButton("enabled", selected.isEnabled())) selected.toggle();
        }
        ImGui.setCursorPosX(10);
        ImGui.dummy(1, 4);
        ImGui.setCursorPosX(10);
        for (Setting<?> setting : selected.getSettings()) {
            if (!setting.isVisible()) continue;
            ImGui.pushID(setting.getRawName());
            drawSetting(setting);
            ImGui.popID();
        }
        ImGui.popID();
    }

    private static void drawSetting(Setting<?> setting) {
        float left = ImGui.getCursorPosX();
        if (setting instanceof BooleanSetting) {
            BooleanSetting value = (BooleanSetting) setting;
            label(setting.getName() + ":");
            if (booleanButton("value", value.isEnabled())) value.toggle();
        } else if (setting instanceof ModeSetting) {
            ModeSetting value = (ModeSetting) setting;
            drawChoices(setting.getName(), value.getModes(), value, null);
        } else if (setting instanceof NumberSetting) {
            NumberSetting value = (NumberSetting) setting;
            label(setting.getName() + ":");
            value.setValue(slider("value", value.getDouble(), value.getMinimum(), value.getMaximum(), value.getIncrement()));
        } else if (setting instanceof RangeSetting) {
            RangeSetting value = (RangeSetting) setting;
            label(setting.getName() + " min:");
            value.setMin(slider("min", value.getMin(), value.getMinimum(), value.getMaximum(), value.getIncrement()));
            ImGui.setCursorPosX(left);
            label(setting.getName() + " max:");
            value.setMax(slider("max", value.getMax(), value.getMinimum(), value.getMaximum(), value.getIncrement()));
        } else if (setting instanceof ColorSetting) {
            ColorSetting value = (ColorSetting) setting;
            label(setting.getName() + ":");
            ImGui.setNextItemWidth(Math.max(30, Math.min(300, ImGui.getContentRegionAvailX() - 12)));
            float[] rgba = {value.getRed() / 255F, value.getGreen() / 255F, value.getBlue() / 255F, value.getAlpha() / 255F};
            if (ImGui.colorEdit4("##value", rgba)) value.setRgba(Math.round(rgba[0] * 255), Math.round(rgba[1] * 255),
                    Math.round(rgba[2] * 255), Math.round(rgba[3] * 255));
        } else if (setting instanceof StringSetting) {
            StringSetting value = (StringSetting) setting;
            label(setting.getName() + ":");
            ImGui.setNextItemWidth(Math.max(30, Math.min(320, ImGui.getContentRegionAvailX() - 12)));
            ImString input = new ImString(value.getValue(), value.getMaxLength() * 4 + 1);
            if (ImGui.inputText("##value", input)) value.setValue(input.get());
        } else if (setting instanceof MultiSelectSetting) {
            MultiSelectSetting value = (MultiSelectSetting) setting;
            if (setting.getRawName().equalsIgnoreCase("Modes")) drawChoices(setting.getName(), value.getOptions(), null, value);
            else drawGroup(value);
        }
        ImGui.setCursorPosX(left);
    }

    private static void drawGroup(MultiSelectSetting value) {
        float left = ImGui.getCursorPosX();
        boolean open = !collapsed.contains(value);
        String title = value.getName() + " (" + value.getValue().size() + ")";
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        if (textButton("group", title, GOLD, textWidth(title) + 18, 24)) {
            if (open) collapsed.add(value); else collapsed.remove(value);
            open = !open;
        }
        float arrow = x + textWidth(title) + 6;
        if (open) ImGui.getWindowDrawList().addTriangleFilled(arrow, y + 8, arrow + 8, y + 8, arrow + 4, y + 14, GOLD);
        else ImGui.getWindowDrawList().addTriangleFilled(arrow, y + 14, arrow + 8, y + 14, arrow + 4, y + 8, GOLD);
        if (open) {
            float start = ImGui.getCursorScreenPosY();
            for (String option : value.getOptions()) {
                ImGui.setCursorPosX(left + 12);
                ImGui.pushID(option);
                label(LanguageManager.translate(option) + ":");
                if (booleanButton("option", value.isSelected(option))) value.toggle(option);
                ImGui.popID();
            }
            ImGui.getWindowDrawList().addLine(x + 3, start, x + 3, ImGui.getCursorScreenPosY() - 7, 0xFF577A9B);
        }
    }

    private static void drawChoices(String name, List<String> options, ModeSetting mode, MultiSelectSetting multi) {
        float left = ImGui.getCursorScreenPosX(), right = left + ImGui.getContentRegionAvailX() - 12;
        ImGui.text(name + ":");
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i), caption = LanguageManager.translate(option);
            boolean comma = i + 1 < options.size();
            nextInline(textWidth(caption) + (comma ? textWidth(",") : 0), left + 14, right, 6);
            boolean active = mode != null ? mode.is(option) : multi.isSelected(option);
            if (textButton("choice-" + option, caption, active ? BLUE : TEXT, textWidth(caption), 24)) {
                if (mode != null) mode.setValue(option); else multi.toggle(option);
            }
            if (comma) { ImGui.sameLine(0, 0); ImGui.text(","); }
        }
    }

    private static void nextInline(float width, float left, float right, float spacing) {
        if (ImGui.getItemRectMaxX() + spacing + width <= right) ImGui.sameLine(0, spacing);
        else ImGui.setCursorScreenPos(left, ImGui.getCursorScreenPosY());
    }

    private static void label(String text) {
        float left = ImGui.getCursorScreenPosX(), right = left + ImGui.getContentRegionAvailX() - 12;
        ImGui.textWrapped(text);
        if (ImGui.getItemRectMaxX() + 6 + 90 <= right) ImGui.sameLine(0, 6);
        else ImGui.setCursorScreenPos(left + 12, ImGui.getCursorScreenPosY());
    }

    private static boolean booleanButton(String id, boolean value) {
        String text = value ? "true" : "false";
        return textButton(id, text, value ? GREEN : RED, textWidth(text), 24);
    }

    private static boolean textButton(String id, String text, int color, float width, float height) {
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, Math.max(1, width), height);
        ImGui.getWindowDrawList().addText(x, y, color, text);
        if (ImGui.isItemHovered()) ImGui.getWindowDrawList().addLine(x, y + 17, x + width, y + 17, color);
        return clicked;
    }

    private static double slider(String id, double value, double min, double max, double increment) {
        float width = Math.max(30, Math.min(200, ImGui.getContentRegionAvailX() - 12));
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton(id, width, 22);
        if (ImGui.isItemActive() && ImGui.isMouseDown(0) && max > min) {
            double fraction = Math.max(0, Math.min(1, (ImGui.getMousePosX() - x - 2) / Math.max(1, width - 4)));
            value = Math.max(min, Math.min(max, min + Math.round(fraction * (max - min) / increment) * increment));
        }
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = ImGui.getStyle().getFrameRounding();
        draw.addRectFilled(x, y, x + width, y + 22, ImGui.isItemHovered() ? 0xFF3A3A3A : BORDER, rounding);
        draw.addRectFilled(x + 2, y + 2, x + width - 2, y + 20, BG, Math.max(0, rounding - 2));
        float fill = max <= min ? 0 : (float) Math.max(0, Math.min(1, (value - min) / (max - min)));
        if (fill > 0) draw.addRectFilled(x + 2, y + 2, x + 2 + (width - 4) * fill, y + 20, BLUE, Math.max(0, rounding - 2));
        String text = BigDecimal.valueOf(value).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        draw.addText(Math.round(x + (width - textWidth(text)) / 2), y + 3, TEXT, text);
        return value;
    }

    private static float textWidth(String text) { return ImGui.calcTextSize(text).x; }

    private static String fitText(String text, float width) {
        if (textWidth(text) <= width) return text;
        int end = text.length();
        while (end > 0 && textWidth(text.substring(0, end) + "...") > width) end--;
        return text.substring(0, end) + "...";
    }

    private static void applyStyle(float rounding, int backgroundAlpha) {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowRounding(rounding); style.setChildRounding(rounding); style.setFrameRounding(rounding / 2);
        style.setPopupRounding(rounding); style.setGrabRounding(rounding / 2); style.setScrollbarRounding(rounding / 2);
        style.setWindowBorderSize(0); style.setChildBorderSize(0); style.setFrameBorderSize(1);
        style.setWindowPadding(0, 0); style.setFramePadding(4, 3); style.setItemSpacing(6, 3); style.setScrollbarSize(8);
        style.setColor(ImGuiCol.Text, TEXT); style.setColor(ImGuiCol.TextDisabled, 0xFF929292);
        // Child windows share the workspace fill; their own backgrounds would
        // multiply opacity and cover the outer window's rounded corners.
        style.setColor(ImGuiCol.WindowBg, RenderUtils.alpha(BG, backgroundAlpha)); style.setColor(ImGuiCol.ChildBg, 0);
        style.setColor(ImGuiCol.PopupBg, RenderUtils.alpha(0xFF202020, backgroundAlpha)); style.setColor(ImGuiCol.Border, BORDER);
        style.setColor(ImGuiCol.FrameBg, BG); style.setColor(ImGuiCol.FrameBgHovered, 0xFF292929);
        style.setColor(ImGuiCol.FrameBgActive, 0xFF303030); style.setColor(ImGuiCol.Button, BORDER);
        style.setColor(ImGuiCol.ButtonHovered, 0xFF383838); style.setColor(ImGuiCol.ButtonActive, 0xFF454545);
        style.setColor(ImGuiCol.Header, 0xFF323232); style.setColor(ImGuiCol.HeaderHovered, 0xFF383838);
        style.setColor(ImGuiCol.HeaderActive, 0xFF454545); style.setColor(ImGuiCol.ScrollbarBg, 0);
        style.setColor(ImGuiCol.ScrollbarGrab, BORDER); style.setColor(ImGuiCol.ScrollbarGrabHovered, 0xFF454545);
        style.setColor(ImGuiCol.ScrollbarGrabActive, 0xFF555555); style.setColor(ImGuiCol.CheckMark, BLUE);
        style.setColor(ImGuiCol.SliderGrab, BLUE); style.setColor(ImGuiCol.SliderGrabActive, BLUE);
        style.setColor(ImGuiCol.TextSelectedBg, 0x887A4A00);
    }
}
