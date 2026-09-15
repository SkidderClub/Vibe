package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.config.VibeConfig;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/** The classic Xanax split-panel layout, backed by the same settings and profiles as every theme. */
final class XanaxWorkspace {
    private static final int TEXT = 0xFFE4E4E4, MUTED = 0xFF858585, RED = 0xFFB7352B;
    private static final int PANEL = 0xFF141414, ROW = 20, CONFIG_WIDTH = 198, CONFIG_HEIGHT = 240;
    private static final NeverLoseFont FONT = new NeverLoseFont(java.awt.Font.PLAIN, 24);
    private static final NeverLoseFont BOLD = new NeverLoseFont(java.awt.Font.BOLD, 24);
    private static final Category[] TABS = {Category.COMBAT, Category.MOVEMENT, Category.VISUAL,
            Category.WORLD, Category.MEME, Category.CLIENT, Category.SCRIPTS};
    private static int savedX = -1, savedY = -1, savedConfigX = -1, savedConfigY = -1;
    private static int savedScreenWidth, savedScreenHeight;
    private static int savedWidth = -1, savedHeight = 598, savedConfigWidth = CONFIG_WIDTH, savedConfigHeight = CONFIG_HEIGHT;
    private static Category savedCategory = Category.COMBAT;
    private static String savedModule;
    private final List<Hit> hits = new ArrayList<Hit>();
    private final SimpleDateFormat clock = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
    private final Pane modulesPane = new Pane(), settingsPane = new Pane();
    private List<String> profiles;
    private int x = savedX, y = savedY, w, h, screenWidth, screenHeight;
    private int preferredWidth = savedWidth, preferredHeight = savedHeight;
    private int preferredConfigWidth = savedConfigWidth, preferredConfigHeight = savedConfigHeight;
    private int configX = savedConfigX, configY = savedConfigY, configWidth = CONFIG_WIDTH, configHeight = CONFIG_HEIGHT;
    private int resizing, resizeOffsetX, resizeOffsetY;
    private int dragging, dragX, dragY, scrollOffset, lastMouseY;
    private boolean compact, showConfigs, withKeybinds = true, creating, selectAll;
    private Category category = savedCategory;
    private Module selected, binding;
    private Hit slider;
    private Pane scrolling;
    private Setting<?> editing;
    private String buffer = "", selectedProfile, deleteCandidate, status = "", date = "";
    private long dateSecond, statusUntil;
    private Popup popup;
    private ColorSetting color;
    private int colorX, colorY, colorDrag = -1;
    private float hue, saturation, brightness;

    void draw(int width, int height, int mx, int my) {
        int previousWidth = screenWidth == 0 ? savedScreenWidth : screenWidth;
        int previousHeight = screenWidth == 0 ? savedScreenHeight : screenHeight;
        if (previousWidth != 0 && (previousWidth != width || previousHeight != height)) {
            resetTransient(); x = y = configX = configY = -1;
        }
        screenWidth = width; screenHeight = height;
        lastMouseY = my;
        if (resizing == 1) {
            preferredWidth = clamp(mx - x + resizeOffsetX, Math.min(300, width - x), Math.min(width - 16, width - x));
            preferredHeight = clamp(my - y + resizeOffsetY, Math.min(220, height - y), Math.min(height - 16, height - y));
        } else if (resizing == 2) {
            preferredConfigWidth = clamp(mx - configX + resizeOffsetX, Math.min(180, width - configX), Math.min(width - 8, width - configX));
            preferredConfigHeight = clamp(my - configY + resizeOffsetY, Math.min(220, height - configY), Math.min(height - 8, height - configY));
        }
        w = preferredWidth < 0 ? Math.min(536, width - (width < 650 ? 16 : CONFIG_WIDTH + 48)) : Math.min(preferredWidth, width - 16);
        h = Math.min(preferredHeight, height - 16);
        compact = width < 650 || w + CONFIG_WIDTH + 48 > width;
        if (x < 0) { x = Math.max(8, (width - w - (compact ? 0 : CONFIG_WIDTH + 32)) / 2); y = (height - h) / 2; }
        if (dragging == 1) { x = mx - dragX; y = my - dragY; }
        x = clamp(x, 0, width - w); y = clamp(y, 0, height - h);
        configWidth = Math.min(preferredConfigWidth, width - 8);
        configHeight = Math.min(preferredConfigHeight, height - 8);
        if (configX < 0) { configX = compact ? width - configWidth - 8 : x + w + 32; configY = (height - configHeight) / 2 + (height > 500 ? 24 : 0); }
        if (dragging == 2) { configX = mx - dragX; configY = my - dragY; }
        configX = clamp(configX, 0, width - configWidth); configY = clamp(configY, 0, height - configHeight);
        if (profiles == null) refreshProfiles();
        List<Module> modules = Vibe.getInstance().getModuleManager().getModules(category);
        if (selected == null || !modules.contains(selected)) {
            selected = null;
            for (Module module : modules) if (module.getId().equals(savedModule)) { selected = module; break; }
            if (selected == null && !modules.isEmpty()) selected = modules.get(0);
            settingsPane.scroll = 0;
        }
        if (slider != null) updateSlider(mx);
        if (colorDrag >= 0) updateColor(mx, my);
        hits.clear();
        GuiRenderState.prepare(false);
        frame(x, y, w, h, 0xFF202020);
        box(x + 5, y + 30, w - 10, h - 36, PANEL);
        long second = System.currentTimeMillis() / 1000;
        if (dateSecond != second) { dateSecond = second; date = "v" + Vibe.VERSION + " [" + clock.format(new Date()) + "]"; }
        NeverLoseFont.REGULAR.draw("Xanax", x + 7, y + 2, RED);
        label(date, x + 7, y + 13, w - 14, TEXT);
        rainbow(x + 7, y + 29, w - 14);
        drawTabs(mx, my);
        int margin = w < 400 ? 14 : 24, gap = w < 400 ? 20 : 36;
        int paneWidth = (w - margin * 2 - gap) / 2;
        modulesPane.layout(x + margin, y + 81, paneWidth, h - 107);
        settingsPane.layout(x + margin + paneWidth + gap, y + 81, paneWidth, h - 107);
        panel(modulesPane, tr("Modules")); panel(settingsPane, tr("Settings"));
        drawModules(modules, mx, my);
        drawSettings();
        if (compact) {
            label(tr("Configs"), x + w - 83, y + h - 20, 59, showConfigs ? TEXT : MUTED);
            hits.add(new Hit(Kind.CONFIGS, x + w - 90, y + h - 23, 68, 18, null, 0));
        }
        String hint = binding != null ? tr("Press a key (Esc to clear)") : selected == null ? "" : selected.getName();
        label(hint, x + margin, y + h - 20, w - margin - (compact ? 100 : 24), MUTED);
        GuiResizeGrip.draw(x + w, y + h, resizing == 1 || GuiResizeGrip.contains(mx, my, x + w, y + h) ? RED : MUTED);
        if (!compact || showConfigs) drawConfigs(mx, my);
        if (color != null) drawColor();
        if (popup != null) drawPopup(mx, my);
    }

    private void drawTabs(int mx, int my) {
        for (int i = 0; i < TABS.length; i++) {
            int left = x + 6 + (w - 12) * i / TABS.length, right = x + 6 + (w - 12) * (i + 1) / TABS.length;
            boolean active = TABS[i] == category;
            box(left, y + 34, right - left, 29, active ? 0xFF171717 : 0xFF121212);
            if (!active && inside(mx, my, left, y + 34, right - left, 29)) Gui.drawRect(left + 1, y + 35, right - 1, y + 62, 0xFF1B1B1B);
            center(TABS[i].getLabel().toUpperCase(Locale.ROOT), left + 2, y + 42, right - left - 4, active ? TEXT : MUTED, true);
            hits.add(new Hit(Kind.CATEGORY, left, y + 34, right - left, 29, TABS[i], 0));
        }
    }

    private void drawModules(List<Module> modules, int mx, int my) {
        Pane p = modulesPane; p.content(modules.size() * ROW);
        try (GuiClip clip = p.clip()) {
            for (int i = 0; i < modules.size(); i++) {
                Module module = modules.get(i); int top = p.top() + i * ROW - p.scroll;
                if (top + ROW <= p.top() || top >= p.bottom()) continue;
                if (module == selected || inside(mx, my, p.x + 4, top, p.w - 10, ROW))
                    Gui.drawRect(p.x + 4, top, p.x + p.w - 5, top + ROW, module == selected ? 0xFF1D1A19 : 0xFF1A1A1A);
                checkbox(p.x + 12, top + 7, module.isEnabled());
                String key = module == binding ? " [...]" : module.getKey() == Keyboard.KEY_NONE ? "" : " [" + Keyboard.getKeyName(module.getKey()) + "]";
                label(module.getName() + key, p.x + 30, top + 3, p.w - 42, TEXT);
                p.hit(new Hit(Kind.MODULE, p.x + 5, top, p.w - 11, ROW, module, 0));
            }
            if (modules.isEmpty()) label(tr("No matching modules"), p.x + 12, p.top() + 4, p.w - 24, MUTED);
        }
        scrollbar(p);
    }

    private void drawSettings() {
        Pane p = settingsPane; int total = 0;
        if (selected != null) for (Setting<?> setting : selected.getSettings()) if (setting.isVisible()) total += settingHeight(setting);
        p.content(total);
        try (GuiClip clip = p.clip()) {
            int top = p.top() - p.scroll;
            if (selected != null) for (Setting<?> setting : selected.getSettings()) if (setting.isVisible()) {
                int size = settingHeight(setting);
                if (top + size > p.top() && top < p.bottom()) drawSetting(setting, p.x + 12, top, p.w - 28);
                top += size;
            }
            if (total == 0) label(tr("No settings"), p.x + 12, p.top() + 4, p.w - 24, MUTED);
        }
        scrollbar(p);
    }

    private static int settingHeight(Setting<?> setting) {
        return setting instanceof BooleanSetting ? ROW : setting instanceof RangeSetting ? 56 : 40;
    }

    private void drawSetting(Setting<?> setting, int left, int top, int width) {
        if (setting instanceof BooleanSetting) {
            checkbox(left, top + 7, ((BooleanSetting) setting).isEnabled());
            label(setting.getName(), left + 20, top + 3, width - 20, TEXT);
            settingsPane.hit(new Hit(Kind.BOOLEAN, left, top, width, ROW, setting, 0));
            return;
        }
        int control = left + 20, cw = width - 34;
        label(setting.getName(), control, top + 3, width - 20, TEXT);
        if (setting instanceof NumberSetting) {
            NumberSetting n = (NumberSetting) setting;
            slider(setting, control, top + 24, cw, n.getDouble(), n.getMinimum(), n.getMaximum(), 0);
        } else if (setting instanceof RangeSetting) {
            RangeSetting r = (RangeSetting) setting;
            slider(setting, control, top + 24, cw, r.getMin(), r.getMinimum(), r.getMaximum(), 0);
            slider(setting, control, top + 40, cw, r.getMax(), r.getMinimum(), r.getMaximum(), 1);
        } else {
            int topControl = top + 19;
            box(control, topControl, width - 20, 20, 0xFF202020);
            boolean dropdown = setting instanceof ModeSetting || setting instanceof MultiSelectSetting;
            int labelWidth = width - (dropdown || setting instanceof ColorSetting ? 44 : 26);
            label(editing == setting ? buffer + caret() : value(setting), control + 3, topControl + 3, labelWidth, MUTED);
            if (dropdown) arrow(left + width - 12, topControl + 8);
            else if (setting instanceof ColorSetting) box(left + width - 16, topControl + 5, 11, 10, ((ColorSetting) setting).getArgb());
            settingsPane.hit(new Hit(dropdown ? Kind.DROPDOWN : setting instanceof ColorSetting ? Kind.COLOR : Kind.TEXT,
                    control, topControl, width - 20, 20, setting, 0));
        }
    }

    private void slider(Setting<?> setting, int left, int top, int width, double value, double min, double max, int index) {
        box(left, top, width, 10, 0xFF242424);
        int fill = Math.round(fraction(value, min, max) * (width - 2));
        if (fill > 0) {
            Gui.drawRect(left + 1, top + 1, left + 1 + fill, top + 9, RED);
            Gui.drawRect(left + 1, top + 1, left + 1 + fill, top + 2, 0xFFD44B3C);
        }
        center(compact(value), left, top, width, TEXT, false);
        text("-", left - 9, top - 1, MUTED); text("+", left + width + 3, top - 1, MUTED);
        settingsPane.hit(new Hit(Kind.SLIDER, left, top - 2, width, 14, setting, index));
        settingsPane.hit(new Hit(Kind.STEP, left - 12, top - 2, 11, 14, setting, index * 2));
        settingsPane.hit(new Hit(Kind.STEP, left + width + 1, top - 2, 12, 14, setting, index * 2 + 1));
    }

    private void drawConfigs(int mx, int my) {
        frame(configX, configY, configWidth, configHeight, 0xFF292929);
        box(configX + 5, configY + 5, configWidth - 10, configHeight - 10, PANEL);
        rainbow(configX + 7, configY + 6, configWidth - 14);
        int left = configX + 10, top = configY + 18, cw = configWidth - 20;
        box(left, top, cw, 20, 0xFF202020);
        label(creating ? buffer + caret() : selectedProfile == null ? tr("No configs") : selectedProfile,
                left + 4, top + 3, cw - 21, creating ? TEXT : MUTED);
        if (!creating) arrow(left + cw - 13, top + 8);
        hits.add(new Hit(Kind.PROFILE, left, top, cw, 20, null, 0));
        String[] buttons = {"Load", "Save", "Create", "Delete", "Folder", "Refresh"};
        int step = (configHeight - 69) / 6;
        for (int i = 0; i < buttons.length; i++) {
            int by = top + 24 + i * step;
            box(left, by, cw, step - 4, inside(mx, my, left, by, cw, step - 4) ? 0xFF292929 : 0xFF1E1E1E);
            border(left + 1, by + 1, cw - 2, step - 6, 0xFF363636);
            center(tr(i == 3 && deleteCandidate != null ? "Confirm delete" : buttons[i]), left, by + (step - 17) / 2, cw, TEXT, true);
            hits.add(new Hit(Kind.ACTION, left, by, cw, step - 4, buttons[i], 0));
        }
        checkbox(left + 3, configY + configHeight - 22, withKeybinds);
        label(tr("Keybinds"), left + 23, configY + configHeight - 25, cw - 42, TEXT);
        hits.add(new Hit(Kind.KEYBINDS, left, configY + configHeight - 28, cw - 10, 22, null, 0));
        GuiResizeGrip.draw(configX + configWidth, configY + configHeight,
                resizing == 2 || GuiResizeGrip.contains(mx, my, configX + configWidth, configY + configHeight) ? RED : MUTED);
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int sy = configY + configHeight + 3;
            if (sy + 18 > screenHeight) sy = configY - 20;
            box(configX, sy, configWidth, 18, PANEL);
            label(tr(status), configX + 5, sy + 2, configWidth - 10, TEXT);
        }
    }

    void click(int mx, int my, int button) {
        boolean overConfigs = (!compact || showConfigs) && inside(mx, my, configX, configY, configWidth, configHeight);
        if (button == 0 && (overConfigs ? GuiResizeGrip.contains(mx, my, configX + configWidth, configY + configHeight)
                : GuiResizeGrip.contains(mx, my, x + w, y + h))) {
            resetTransient(); resizing = overConfigs ? 2 : 1;
            resizeOffsetX = (overConfigs ? configX + configWidth : x + w) - mx;
            resizeOffsetY = (overConfigs ? configY + configHeight : y + h) - my;
            if (overConfigs) showConfigs = true;
            return;
        }
        if (popup != null) { clickPopup(mx, my, button); return; }
        if (color != null) {
            if (inside(mx, my, colorX, colorY, 184, 182)) { clickColor(mx, my, button); return; }
            release(0); finishEditing(); color = null; return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if (overConfigs && hit.kind != Kind.PROFILE && hit.kind != Kind.ACTION && hit.kind != Kind.KEYBINDS) continue;
            if (hit.kind != Kind.TEXT && hit.kind != Kind.PROFILE && hit.kind != Kind.ACTION) finishEditing();
            if (hit.kind != Kind.PROFILE && hit.kind != Kind.ACTION) creating = false;
            if (hit.kind != Kind.ACTION) deleteCandidate = null;
            switch (hit.kind) {
                case CATEGORY:
                    if (button == 0) { resetTransient(); category = (Category) hit.value; selected = null; modulesPane.scroll = settingsPane.scroll = 0; } return;
                case MODULE:
                    if (button > 2) return;
                    Module module = (Module) hit.value;
                    if (selected != module) { resetTransient(); selected = module; settingsPane.scroll = 0; }
                    if (button == 2) binding = module;
                    else if (button == 0) module.toggle();
                    hits.clear(); return;
                case BOOLEAN:
                    if (button == 0) { ((BooleanSetting) hit.value).toggle(); save(); hits.clear(); } return;
                case SLIDER: if (button == 0) { slider = hit; updateSlider(mx); } return;
                case STEP: if (button == 0) { step(hit); save(); } return;
                case DROPDOWN:
                    if (button == 0) openPopup(hit, hit.value instanceof ModeSetting ? ((ModeSetting) hit.value).getModes() : ((MultiSelectSetting) hit.value).getOptions()); return;
                case TEXT:
                    if (button == 0) { finishEditing(); editing = (Setting<?>) hit.value; buffer = value(editing); } return;
                case COLOR:
                    if (button == 0) {
                        color = (ColorSetting) hit.value;
                        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
                        colorX = clamp(hit.x, 4, screenWidth - 188); colorY = clamp(hit.y + hit.h + 3, 4, screenHeight - 186);
                    } return;
                case SCROLL:
                    if (button == 0) { scrolling = (Pane) hit.value; scrollOffset = my >= hit.index && my < hit.index + scrolling.handle() ? my - hit.index : scrolling.handle() / 2; dragScroll(my); } return;
                case CONFIGS: if (button == 0) { showConfigs = !showConfigs; resetTransient(); } return;
                case PROFILE:
                    if (button == 0 && !creating) { finishEditing(); refreshProfiles(); openPopup(hit, profiles); } return;
                case KEYBINDS: if (button == 0) withKeybinds = !withKeybinds; return;
                case ACTION: if (button == 0) action((String) hit.value); return;
                default: return;
            }
        }
        finishEditing(); deleteCandidate = null;
        if (button != 0) return;
        if (overConfigs) { dragging = 2; dragX = mx - configX; dragY = my - configY; }
        else if (inside(mx, my, x, y, w, 28)) { dragging = 1; dragX = mx - x; dragY = my - y; }
    }

    void wheel(int mx, int my, int amount) {
        if (amount == 0 || resizing != 0) return;
        if (popup != null) {
            if (inside(mx, my, popup.x, popup.y, popup.w, popup.h)) popup.scroll = clamp(popup.scroll + (amount > 0 ? -40 : 40), 0, popup.options.size() * ROW - popup.h + 4);
            return;
        }
        if (color != null || slider != null || scrolling != null) return;
        if ((!compact || showConfigs) && inside(mx, my, configX, configY, configWidth, configHeight)) return;
        for (Pane p : new Pane[] {modulesPane, settingsPane}) if (inside(mx, my, p.x, p.y, p.w, p.h)) {
            finishEditing(); p.scroll = clamp(p.scroll + (amount > 0 ? -40 : 40), 0, p.maxScroll); hits.clear(); return;
        }
    }

    void release(int button) {
        if (button != 0) return;
        if (slider != null || colorDrag >= 0) save();
        dragging = 0; resizing = 0; slider = null; scrolling = null; colorDrag = -1;
    }

    boolean key(char character, int key) {
        if (binding != null) { binding.setKey(key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_DELETE ? Keyboard.KEY_NONE : key); binding = null; return true; }
        if (creating || editing != null) {
            if (key == Keyboard.KEY_ESCAPE) { editing = null; creating = false; selectAll = false; return true; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { if (creating) createProfile(); else finishEditing(); return true; }
            boolean ctrl = Keyboard.isCreated() && GuiScreen.isCtrlKeyDown();
            if (ctrl && key == Keyboard.KEY_A) { selectAll = true; return true; }
            if (key != Keyboard.KEY_BACK && !(ctrl && key == Keyboard.KEY_V) && !ChatAllowedCharacters.isAllowedCharacter(character)) return true;
            if (selectAll) buffer = "";
            selectAll = false;
            if (key == Keyboard.KEY_BACK) buffer = buffer.isEmpty() ? buffer : buffer.substring(0, buffer.length() - 1);
            else if (ctrl && key == Keyboard.KEY_V) buffer += ChatAllowedCharacters.filterAllowedCharacters(GuiScreen.getClipboardString());
            else buffer += character;
            buffer = buffer.substring(0, Math.min(buffer.length(), creating ? 64 : 256));
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (popup != null || color != null) { release(0); popup = null; color = null; return true; }
            if (compact && showConfigs) { showConfigs = false; return true; }
        }
        return false;
    }

    void close() {
        finishEditing(); release(0);
        if (screenWidth != 0) {
            savedX = x; savedY = y; savedConfigX = configX; savedConfigY = configY;
            savedScreenWidth = screenWidth; savedScreenHeight = screenHeight;
            savedWidth = preferredWidth; savedHeight = preferredHeight;
            savedConfigWidth = preferredConfigWidth; savedConfigHeight = preferredConfigHeight;
            savedCategory = category; savedModule = selected == null ? null : selected.getId();
        }
        resetTransient();
    }

    private void resetTransient() {
        finishEditing(); release(0); popup = null; color = null; binding = null; creating = false; deleteCandidate = null; hits.clear();
    }

    private void finishEditing() {
        if (editing instanceof StringSetting) ((StringSetting) editing).setValue(buffer);
        else if (editing instanceof ColorSetting) {
            ColorSetting value = (ColorSetting) editing;
            value.setHex(buffer);
            float[] hsv = Color.RGBtoHSB(value.getRed(), value.getGreen(), value.getBlue(), null);
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
        }
        if (editing != null) save();
        editing = null; selectAll = false;
    }

    private void updateSlider(int mx) {
        double progress = fraction(mx, slider.x, slider.x + slider.w);
        if (slider.value instanceof NumberSetting) {
            NumberSetting n = (NumberSetting) slider.value; n.setValue(n.getMinimum() + progress * (n.getMaximum() - n.getMinimum()));
        } else {
            RangeSetting r = (RangeSetting) slider.value; double value = r.getMinimum() + progress * (r.getMaximum() - r.getMinimum());
            if (slider.index == 0) r.setMin(Math.min(r.getMax(), value)); else r.setMax(Math.max(r.getMin(), value));
        }
    }

    private void step(Hit hit) {
        int direction = hit.index % 2 == 0 ? -1 : 1;
        if (hit.value instanceof NumberSetting) { NumberSetting n = (NumberSetting) hit.value; n.setValue(n.getDouble() + direction * n.getIncrement()); }
        else {
            RangeSetting r = (RangeSetting) hit.value;
            if (hit.index / 2 == 0) r.setMin(Math.min(r.getMax(), r.getMin() + direction * r.getIncrement()));
            else r.setMax(Math.max(r.getMin(), r.getMax() + direction * r.getIncrement()));
        }
    }

    private void openPopup(Hit anchor, List<String> options) {
        if (options.isEmpty()) return;
        int pw = Math.min(screenWidth - 8, Math.max(120, anchor.w));
        int ph = Math.min(Math.min(204, screenHeight - 8), options.size() * ROW + 4);
        popup = new Popup(anchor.value, new ArrayList<String>(options), clamp(anchor.x, 4, screenWidth - pw - 4),
                clamp(anchor.y + anchor.h + 2, 4, screenHeight - ph - 4), pw, ph);
    }

    private void drawPopup(int mx, int my) {
        Popup p = popup; box(p.x, p.y, p.w, p.h, 0xFF202020); border(p.x + 1, p.y + 1, p.w - 2, p.h - 2, 0xFF373737);
        try (GuiClip clip = new GuiClip(p.x + 2, p.y + 2, p.w - 4, p.h - 4)) {
            for (int i = 0; i < p.options.size(); i++) {
                int top = p.y + 2 + i * ROW - p.scroll;
                if (top + ROW <= p.y + 2 || top >= p.y + p.h - 2) continue;
                String option = p.options.get(i);
                boolean active = p.value instanceof ModeSetting ? ((ModeSetting) p.value).is(option)
                        : p.value instanceof MultiSelectSetting ? ((MultiSelectSetting) p.value).isSelected(option) : option.equals(selectedProfile);
                if (inside(mx, my, p.x + 2, top, p.w - 4, ROW)) Gui.drawRect(p.x + 2, top, p.x + p.w - 2, top + ROW, 0xFF303030);
                checkbox(p.x + 7, top + 7, active);
                label(p.value == null ? option : tr(option), p.x + 21, top + 3, p.w - 28, active ? TEXT : MUTED);
            }
        }
        int max = p.options.size() * ROW - p.h + 4;
        if (max > 0) {
            int handle = Math.max(10, (p.h - 4) * (p.h - 4) / (p.options.size() * ROW));
            int top = p.y + 2 + (p.h - 4 - handle) * p.scroll / max;
            Gui.drawRect(p.x + p.w - 3, top, p.x + p.w - 2, top + handle, RED);
        }
    }

    private void clickPopup(int mx, int my, int button) {
        Popup p = popup;
        if (!inside(mx, my, p.x, p.y, p.w, p.h)) { popup = null; return; }
        if (button != 0 || my < p.y + 2 || my >= p.y + p.h - 2) return;
        int index = (my - p.y - 2 + p.scroll) / ROW;
        if (index < 0 || index >= p.options.size()) return;
        String option = p.options.get(index);
        if (p.value instanceof MultiSelectSetting) { ((MultiSelectSetting) p.value).toggle(option); save(); }
        else {
            popup = null;
            if (p.value instanceof ModeSetting) { ((ModeSetting) p.value).setValue(option); save(); }
            else { selectedProfile = option; deleteCandidate = null; }
        }
        hits.clear();
    }

    private void action(String action) {
        VibeConfig config = Vibe.getInstance().getConfig(); if (config == null) return;
        finishEditing();
        if (!"Delete".equals(action)) deleteCandidate = null;
        if ("Create".equals(action)) {
            if (creating) createProfile();
            else { creating = true; buffer = ""; status("Enter a config name"); }
            return;
        }
        creating = false;
        if ("Refresh".equals(action)) { refreshProfiles(); status("Refreshed"); }
        else if ("Folder".equals(action)) { if (!config.openDirectory()) status("Could not open folder"); }
        else if (selectedProfile == null) status("No configs");
        else if ("Load".equals(action)) {
            boolean loaded = config.load(selectedProfile, Vibe.getInstance().getModuleManager(), withKeybinds, true);
            resetTransient(); status(loaded ? "Loaded" : "Could not load config");
        } else if ("Save".equals(action)) status(config.save(selectedProfile, Vibe.getInstance().getModuleManager()) ? "Saved" : "Could not save config");
        else if ("Delete".equals(action)) {
            if (!selectedProfile.equals(deleteCandidate)) { deleteCandidate = selectedProfile; return; }
            boolean active = selectedProfile.equals(config.getActiveName());
            boolean deleted = config.delete(selectedProfile); deleteCandidate = null;
            if (deleted && active) {
                // Autosave must not recreate a profile the user just deleted or overwrite another profile.
                String replacement = "default"; int suffix = 1;
                List<String> existing = config.list();
                while (existing.contains(replacement) || replacement.equals(selectedProfile)) replacement = "default-" + suffix++;
                config.save(replacement, Vibe.getInstance().getModuleManager());
            }
            refreshProfiles(); status(deleted ? "Deleted" : "Could not delete config");
        }
    }

    private void createProfile() {
        String name = buffer.trim();
        if (!name.matches("[a-zA-Z0-9_-]{1,64}")) { status("Use letters, numbers, - or _"); return; }
        VibeConfig config = Vibe.getInstance().getConfig(); if (config == null) return;
        for (String existing : config.list()) if (existing.equalsIgnoreCase(name)) { status("Config already exists"); return; }
        if (config.save(name, Vibe.getInstance().getModuleManager())) { selectedProfile = name; creating = false; refreshProfiles(); status("Created"); }
        else status("Could not save config");
    }

    private void refreshProfiles() {
        VibeConfig config = Vibe.getInstance().getConfig();
        profiles = config == null ? new ArrayList<String>() : config.list();
        if (selectedProfile == null || !profiles.contains(selectedProfile))
            selectedProfile = config != null && profiles.contains(config.getActiveName()) ? config.getActiveName() : profiles.isEmpty() ? null : profiles.get(0);
    }

    private void status(String value) { status = value; statusUntil = System.currentTimeMillis() + 4000; }

    private void drawColor() {
        frame(colorX, colorY, 184, 182, PANEL);
        label(color.getName(), colorX + 10, colorY + 7, 164, TEXT);
        int left = colorX + 10, top = colorY + 27;
        for (int iy = 0; iy < 48; iy++) for (int ix = 0; ix < 66; ix++)
            Gui.drawRect(left + ix * 2, top + iy * 2, left + ix * 2 + 2, top + iy * 2 + 2, Color.HSBtoRGB(hue, ix / 65F, 1 - iy / 47F));
        for (int i = 0; i < 96; i++) Gui.drawRect(left + 140, top + i, left + 151, top + i + 1, Color.HSBtoRGB(i / 95F, 1, 1));
        border(left + Math.round(saturation * 131) - 2, top + Math.round((1 - brightness) * 95) - 2, 5, 5, TEXT);
        Gui.drawRect(left + 138, top + Math.round(hue * 95) - 1, left + 153, top + Math.round(hue * 95) + 1, TEXT);
        RenderUtils.transparencyGrid(left, top + 106, left + 164, top + 116, 4);
        for (int i = 0; i < 164; i++) Gui.drawRect(left + i, top + 106, left + i + 1, top + 116, RenderUtils.alpha(color.getArgb(), i * 255 / 163));
        int alphaX = left + color.getAlpha() * 163 / 255;
        Gui.drawRect(alphaX, top + 105, alphaX + 1, top + 117, TEXT);
        box(left, top + 125, 164, 21, 0xFF202020);
        label(editing == color ? buffer + caret() : color.getHex(), left + 5, top + 128, 154, TEXT);
    }

    private void clickColor(int mx, int my, int button) {
        if (button != 0) return;
        int left = colorX + 10, top = colorY + 27;
        if (inside(mx, my, left, top + 125, 164, 21)) { finishEditing(); editing = color; buffer = color.getHex(); return; }
        finishEditing();
        if (inside(mx, my, left, top, 132, 96)) colorDrag = 0;
        else if (inside(mx, my, left + 138, top, 15, 96)) colorDrag = 1;
        else if (inside(mx, my, left, top + 104, 164, 14)) colorDrag = 2;
        if (colorDrag >= 0) updateColor(mx, my);
    }

    private void updateColor(int mx, int my) {
        int left = colorX + 10, top = colorY + 27;
        if (colorDrag == 0) { saturation = fraction(mx, left, left + 131); brightness = 1 - fraction(my, top, top + 95); }
        else if (colorDrag == 1) hue = fraction(my, top, top + 95);
        int alpha = colorDrag == 2 ? Math.round(fraction(mx, left, left + 163) * 255) : color.getAlpha();
        color.setValue((alpha << 24) | (Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF));
    }

    private void scrollbar(Pane p) {
        Gui.drawRect(p.x + p.w - 7, p.top(), p.x + p.w - 5, p.bottom(), 0xFF070707);
        if (p.maxScroll <= 0) return;
        int top = p.top() + (p.bottom() - p.top() - p.handle()) * p.scroll / p.maxScroll;
        Gui.drawRect(p.x + p.w - 7, top, p.x + p.w - 5, top + p.handle(), RED);
        hits.add(new Hit(Kind.SCROLL, p.x + p.w - 10, p.top(), 8, p.bottom() - p.top(), p, top));
    }

    private void dragScroll(int my) {
        Pane p = scrolling;
        p.scroll = clamp(Math.round((my - p.top() - scrollOffset) * p.maxScroll / (float) Math.max(1, p.bottom() - p.top() - p.handle())), 0, p.maxScroll);
    }

    private static void panel(Pane p, String title) {
        box(p.x, p.y, p.w, p.h, PANEL); border(p.x + 2, p.y + 2, p.w - 4, p.h - 4, 0xFF222222);
        int titleWidth = (int) BOLD.width(title);
        Gui.drawRect(p.x + 9, p.y - 5, p.x + 15 + titleWidth, p.y + 7, PANEL);
        BOLD.draw(title, p.x + 12, p.y - 7, TEXT);
    }

    private static void checkbox(int x, int y, boolean enabled) { box(x, y, 8, 8, enabled ? RED : 0xFF242424); }
    private static void frame(int x, int y, int w, int h, int fill) {
        box(x, y, w, h, fill); border(x + 1, y + 1, w - 2, h - 2, 0xFF454545); border(x + 3, y + 3, w - 6, h - 6, 0xFF111111);
    }
    private static void box(int x, int y, int w, int h, int fill) { Gui.drawRect(x, y, x + w, y + h, fill); border(x, y, w, h, 0xFF080808); }
    private static void border(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 1, color); Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color); Gui.drawRect(x + w - 1, y, x + w, y + h, color);
    }
    private static void rainbow(int x, int y, int width) {
        int[] stops = {0xFFDFD976, 0xFF81D7B4, 0xFF8EA4E9, 0xFFCF83D5, 0xFFDC707E, 0xFFE4CF79};
        for (int i = 0; i < width; i++) {
            float pos = i * (stops.length - 1F) / Math.max(1, width - 1); int index = Math.min(stops.length - 2, (int) pos);
            int a = stops[index], b = stops[index + 1]; float t = pos - index;
            int red = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
            int green = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
            int blue = Math.round((a & 255) * (1 - t) + (b & 255) * t);
            Gui.drawRect(x + i, y, x + i + 1, y + 2, 0xFF000000 | red << 16 | green << 8 | blue);
        }
    }
    private static void arrow(int x, int y) { for (int i = 0; i < 4; i++) Gui.drawRect(x + i, y + i, x + 7 - i, y + i + 1, MUTED); }
    private static void text(String value, int x, int y, int color) { FONT.draw(value, x, y, color); }
    private static void label(String value, int x, int y, int width, int color) { text(FONT.fit(value, Math.max(0, width)), x, y, color); }
    private static void center(String value, int x, int y, int width, int color, boolean bold) {
        NeverLoseFont font = bold ? BOLD : FONT;
        String fitted = font.fit(value, width); font.draw(fitted, x + (width - font.width(fitted)) / 2F, y, color);
    }
    private static String tr(String value) { return LanguageManager.translate(value); }
    private static String caret() { return System.currentTimeMillis() % 1000 < 500 ? "|" : ""; }
    private static String compact(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static String value(Setting<?> setting) {
        if (setting instanceof ColorSetting) return ((ColorSetting) setting).getHex();
        if (setting instanceof MultiSelectSetting) {
            StringBuilder result = new StringBuilder();
            for (String option : ((MultiSelectSetting) setting).getValue()) { if (result.length() > 0) result.append(", "); result.append(tr(option)); }
            return result.length() == 0 ? tr("None") : result.toString();
        }
        return setting instanceof ModeSetting ? tr(String.valueOf(setting.getValue())) : String.valueOf(setting.getValue());
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
    private static float fraction(double value, double min, double max) { return max <= min ? 0 : (float) Math.max(0, Math.min(1, (value - min) / (max - min))); }
    private static void save() { if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); }

    private final class Pane {
        int x, y, w, h, scroll, maxScroll;
        void layout(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        int top() { return y + 12; } int bottom() { return y + h - 8; }
        void content(int height) {
            maxScroll = Math.max(0, height - (bottom() - top())); scroll = clamp(scroll, 0, maxScroll);
            if (scrolling == this) dragScroll(lastMouseY);
        }
        int handle() { int track = bottom() - top(); return Math.min(track, Math.max(16, track * track / Math.max(1, track + maxScroll))); }
        GuiClip clip() { return new GuiClip(x + 4, top(), w - 12, Math.max(0, bottom() - top())); }
        void hit(Hit hit) {
            int top = Math.max(top(), hit.y), bottom = Math.min(bottom(), hit.y + hit.h);
            if (bottom > top) hits.add(new Hit(hit.kind, hit.x, top, hit.w, bottom - top, hit.value, hit.index));
        }
    }
    private enum Kind { CATEGORY, MODULE, BOOLEAN, SLIDER, STEP, DROPDOWN, TEXT, COLOR, SCROLL, CONFIGS, PROFILE, ACTION, KEYBINDS }
    private static final class Hit {
        final Kind kind; final int x, y, w, h, index; final Object value;
        Hit(Kind kind, int x, int y, int w, int h, Object value, int index) { this.kind = kind; this.x = x; this.y = y; this.w = w; this.h = h; this.value = value; this.index = index; }
        boolean contains(int mx, int my) { return inside(mx, my, x, y, w, h); }
    }
    private static final class Popup {
        final Object value; final List<String> options; final int x, y, w, h; int scroll;
        Popup(Object value, List<String> options, int x, int y, int w, int h) { this.value = value; this.options = options; this.x = x; this.y = y; this.w = w; this.h = h; }
    }
}
