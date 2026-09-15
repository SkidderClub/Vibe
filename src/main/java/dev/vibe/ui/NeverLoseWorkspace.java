package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import dev.vibe.setting.*;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import static dev.vibe.ui.NeverLoseStyle.*;

/** Self-contained NeverLose layout and input. Values remain in Vibe's module model. */
final class NeverLoseWorkspace {
    private static final int ROW = 28, HEADER = 44;
    private static int savedX = -1, savedY = -1, savedScroll;
    private static int savedWidth = 640, savedHeight = 460;
    private static Category savedCategory = Category.COMBAT;
    /** Settings start closed every time; modules without settings are never foldable. */
    private static final Set<String> EXPANDED = new HashSet<String>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final Map<Object, Float> switches = new IdentityHashMap<Object, Float>();
    private int x = savedX, y = savedY, w, h, sidebar, screenWidth, screenHeight;
    private int preferredWidth = savedWidth, preferredHeight = savedHeight;
    private boolean resizing;
    private int resizeOffsetX, resizeOffsetY;
    private int contentLeft, contentTop, contentRight, contentBottom, scroll = savedScroll, maxScroll;
    private Category category = savedCategory;
    private String query = "", buffer = "";
    private boolean searching, dragging, scrollbarDragging, selectAll;
    private int dragX, dragY, scrollbarOffset;
    private Hit slider;
    private boolean upperHandle;
    private Module binding;
    private Setting<?> editing;
    private Popup popup;
    private ColorSetting color;
    private float hue, saturation, brightness;
    private int colorX, colorY, colorDrag = -1;
    private Object hovered, lastHovered;
    private String tooltip;
    private long hoverSince, lastFrame;
    private float delta;

    void draw(int width, int height, int mouseX, int mouseY) {
        if (screenWidth != 0 && (screenWidth != width || screenHeight != height)) {
            release(0);
            resetTransient();
        }
        screenWidth = width; screenHeight = height;
        if (resizing) {
            preferredWidth = clamp(mouseX - x + resizeOffsetX, Math.min(320, width - x), Math.min(width - 16, width - x));
            preferredHeight = clamp(mouseY - y + resizeOffsetY, Math.min(240, height - y), Math.min(height - 16, height - y));
        }
        w = Math.min(preferredWidth, Math.max(1, width - 16)); h = Math.min(preferredHeight, Math.max(1, height - 16));
        sidebar = w < 430 ? 100 : 128;
        if (x < 0) { x = (width - w) / 2; y = (height - h) / 2; }
        if (dragging) { x = mouseX - dragX; y = mouseY - dragY; }
        x = clamp(x, 0, width - w); y = clamp(y, 0, height - h);
        contentLeft = x + sidebar + 10; contentRight = x + w - 12;
        contentTop = y + HEADER + 10; contentBottom = y + h - 18;
        long now = System.currentTimeMillis(); delta = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 85F); lastFrame = now;
        if (slider != null) updateSlider(mouseX);
        if (colorDrag >= 0) updateColor(mouseX, mouseY);
        if (scrollbarDragging) dragScrollbar(mouseY);
        hits.clear(); hovered = null; tooltip = null;
        GuiRenderState.prepare(false);
        for (int i = 6; i > 0; i--) rect(x - i, y - i + 2, w + i * 2, h + i * 2, 8 + i, 0x08000000);
        surface(x, y, w, h, 8, BACKGROUND);
        rect(x + 1, y + 1, sidebar, h - 2, 7, SIDEBAR);
        Gui.drawRect(x + sidebar - 6, y + 1, x + sidebar, y + h - 1, SIDEBAR);
        Gui.drawRect(x + sidebar, y + 1, x + sidebar + 1, y + h - 1, BORDER);
        drawSidebar(mouseX, mouseY);
        drawHeader(mouseX, mouseY);
        drawModules(mouseX, mouseY);
        if (maxScroll > 0) {
            int track = contentBottom - contentTop, handle = scrollbarHeight();
            int top = contentTop + Math.round((track - handle) * scroll / (float) maxScroll);
            rect(x + w - 6, contentTop, 2, track, 1, BORDER);
            rect(x + w - 6, top, 2, handle, 1, scrollbarDragging ? ACCENT : DIM);
            hits.add(new Hit(Kind.SCROLL, x + w - 9, contentTop, 8, track, null, top));
        }
        if (color != null) drawColor();
        if (popup != null) drawPopup(mouseX, mouseY);
        GuiResizeGrip.draw(x + w, y + h, resizing || GuiResizeGrip.contains(mouseX, mouseY, x + w, y + h) ? ACCENT : DIM);
        if (hovered != lastHovered) { lastHovered = hovered; hoverSince = now; }
        if (popup == null && color == null && slider == null && !dragging && !resizing && tooltip != null && now - hoverSince > 650) drawTooltip(mouseX, mouseY);
    }

    private void drawSidebar(int mx, int my) {
        rect(x + 13, y + 14, 19, 17, 5, 0xFF122B43);
        NeverLoseFont.BOLD.draw("NL", x + 15, y + 16, 0xFFE1F4FF);
        if (sidebar > 100) {
            NeverLoseFont.BOLD.draw("Neverlose", x + 41, y + 10, 0xFFF0F2F6);
            small("Vibe Client", x + 41, y + 25, DIM);
        } else small("NEVERLOSE", x + 37, y + 18, TEXT);
        Gui.drawRect(x + 9, y + HEADER, x + sidebar - 9, y + HEADER + 1, BORDER);
        int row = h < 330 ? Math.max(18, (h - 99) / 7) : 27;
        int tabY = y + HEADER + (h < 330 ? 10 : 26);
        if (h >= 330) small("GAMEPLAY", x + 14, tabY - 16, DIM);
        for (Category value : Category.values()) {
            if (value == Category.CLIENT && h >= 330) { tabY += 23; small("WORKSPACE", x + 14, tabY - 16, DIM); }
            boolean active = category == value && query.isEmpty();
            boolean hover = inside(mx, my, x + 6, tabY, sidebar - 12, row - 2);
            if (active || hover) rect(x + 6, tabY, sidebar - 12, row - 2, 5, active ? 0xFF252A31 : 0xFF191E25);
            icon(value, x + 15, tabY + (row - 12) / 2, active ? ACCENT : MUTED);
            label(value.getLabel(), x + 34, tabY + (row - 13) / 2, sidebar - 42, active ? 0xFFF0F2F6 : MUTED);
            hits.add(new Hit(Kind.CATEGORY, x + 6, tabY, sidebar - 12, row - 2, value, 0));
            tabY += row;
        }
        int footer = y + h - 43;
        Gui.drawRect(x + 10, footer, x + sidebar - 10, footer + 1, BORDER);
        circle(x + 23, footer + 22, 11, 0xFF284D7C);
        circle(x + 23, footer + 19, 3, ACCENT);
        line(ACCENT, x + 17, footer + 27, x + 19, footer + 24, x + 27, footer + 24, x + 29, footer + 27);
        String user = Vibe.getInstance().getIdentity() == null ? "Vibe User" : Vibe.getInstance().getIdentity().getGamertag();
        if (user == null || user.trim().isEmpty()) user = "Vibe User";
        label(user, x + 41, footer + 10, sidebar - 49, TEXT);
        rect(x + 42, footer + 27, 4, 4, 2, 0xFF4ABF91);
        small("Vibe " + Vibe.VERSION, x + 50, footer + 24, MUTED);
    }

    private void drawHeader(int mx, int my) {
        int left = x + sidebar + 10, top = y + 10;
        Gui.drawRect(x + sidebar + 1, y + HEADER, x + w - 1, y + HEADER + 1, BORDER);
        int configWidth = Math.min(158, Math.max(77, (w - sidebar) / 3));
        surface(left, top, configWidth, 24, 4, 0xFF101319);
        line(MUTED, left + 9, top + 7, left + 17, top + 7, left + 17, top + 16, left + 9, top + 16, left + 9, top + 7);
        line(MUTED, left + 11, top + 7, left + 11, top + 10, left + 15, top + 10, left + 15, top + 7);
        String profile = Vibe.getInstance().getConfig() == null ? "default" : Vibe.getInstance().getConfig().getActiveName();
        label(profile, left + 25, top + 5, configWidth - 42, TEXT); chevron(left + configWidth - 13, top + 10, false, MUTED);
        hits.add(new Hit(Kind.CONFIG, left, top, configWidth, 24, null, 0));
        left += configWidth + 7;
        if (w - sidebar >= 390) {
            surface(left, top, 70, 24, 4, 0xFF101319);
            label(category.getLabel(), left + 9, top + 5, 54, MUTED); left += 78;
        }
        int searchWidth = x + w - 12 - left;
        surface(left, top, searchWidth, 24, 4, searching ? 0xFF1A202A : 0xFF101319);
        search(left + 8, top + 7, searching ? ACCENT : MUTED);
        String searchText = query.isEmpty() && !searching ? tr("Search") : query + (searching && System.currentTimeMillis() % 1000 < 500 ? "|" : "");
        label(searchText, left + 24, top + 5, searchWidth - 31, query.isEmpty() ? DIM : TEXT);
        hits.add(new Hit(Kind.SEARCH, left, top, searchWidth, 24, null, 0));
    }

    private List<Module> modules() {
        List<Module> result = new ArrayList<Module>();
        String filter = query.toLowerCase(Locale.ROOT).trim();
        for (Module module : Vibe.getInstance().getModuleManager().getModules()) {
            if (filter.isEmpty() ? module.getCategory() == category : matches(module, filter)) result.add(module);
        }
        return result;
    }

    private boolean matches(Module module, String filter) {
        if (module.getName().toLowerCase(Locale.ROOT).contains(filter) || module.getRawName().toLowerCase(Locale.ROOT).contains(filter)) return true;
        for (Setting<?> setting : module.getSettings()) if (setting.isVisible() && (setting.getName().toLowerCase(Locale.ROOT).contains(filter)
                || setting.getRawName().toLowerCase(Locale.ROOT).contains(filter))) return true;
        return false;
    }

    private void drawModules(int mx, int my) {
        List<Module> modules = modules();
        int columns = contentRight - contentLeft >= 400 ? 2 : 1;
        int cardWidth = (contentRight - contentLeft - (columns - 1) * 10) / columns;
        int[] bottoms = new int[columns];
        List<Hit> cards = new ArrayList<Hit>();
        for (Module module : modules) {
            int column = columns == 1 || bottoms[0] <= bottoms[1] ? 0 : 1;
            int size = cardHeight(module);
            cards.add(new Hit(Kind.FOLD, contentLeft + column * (cardWidth + 10), bottoms[column], cardWidth, size, module, 0));
            bottoms[column] += size + 12;
        }
        int total = 0; for (int bottom : bottoms) total = Math.max(total, bottom);
        maxScroll = Math.max(0, total - 12 - (contentBottom - contentTop));
        scroll = clamp(scroll, 0, maxScroll);
        try (GuiClip clip = new GuiClip(contentLeft, contentTop, contentRight - contentLeft, contentBottom - contentTop)) {
            for (Hit card : cards) {
                int top = contentTop + card.y - scroll;
                if (top + card.h <= contentTop || top >= contentBottom) continue;
                drawCard((Module) card.value, card.x, top, card.w, card.h, mx, my);
            }
            if (modules.isEmpty()) {
                text(tr("No matching modules"), contentLeft + 12, contentTop + 22, TEXT);
                label(tr("Try another search or category."), contentLeft + 12, contentTop + 43, cardWidth - 24, MUTED);
            }
        }
    }

    private int cardHeight(Module module) {
        int height = 19 + ROW + 4;
        if (EXPANDED.contains(module.getId())) for (Setting<?> setting : module.getSettings()) if (setting.isVisible()) height += setting instanceof RangeSetting ? 42 : ROW;
        return height;
    }

    private void drawCard(Module module, int left, int top, int width, int height, int mx, int my) {
        boolean expandable = hasVisibleSettings(module);
        boolean expanded = EXPANDED.contains(module.getId());
        small(NeverLoseFont.REGULAR.fit(module.getName().toUpperCase(Locale.ROOT), (width - 24) / .8F), left + 8, top + 1, MUTED);
        if (expandable) {
            chevron(left + width - 14, top + 5, expanded, DIM);
            contentHit(new Hit(Kind.FOLD, left, top, width, 18, module, 0));
        }
        if (inside(mx, my, left, top, width, 18) && insideContent(mx, my)) hover(module, module.getDescription() + (expandable ? "  \u00b7  " + tr("Click to expand") : "") + "  \u00b7  " + tr("Middle click: bind"));
        int body = top + 19;
        surface(left, body, width, height - 19, 7, CARD);
        label(tr("Enabled"), left + 10, body + 7, width - 98, TEXT);
        float progress = animate(module, module.isEnabled());
        toggle(left + width - 36, body + 8, module.isEnabled(), progress);
        String key = binding == module ? "..." : module.getKey() == Keyboard.KEY_NONE ? "\u00b7\u00b7\u00b7" : Keyboard.getKeyName(module.getKey());
        label(key == null ? "?" : key, left + width - 70, body + 7, 29, binding == module ? ACCENT : DIM);
        contentHit(new Hit(Kind.MODULE, left + 1, body, width - 2, ROW, module, 0));
        contentHit(new Hit(Kind.BIND, left + width - 74, body + 2, 34, ROW - 4, module, 0));
        if (expanded) {
            int rowY = body + ROW;
            for (Setting<?> setting : module.getSettings()) if (setting.isVisible()) {
                drawSetting(setting, left + 10, rowY, width - 20, mx, my); rowY += setting instanceof RangeSetting ? 42 : ROW;
            }
        }
    }

    private void drawSetting(Setting<?> setting, int left, int top, int width, int mx, int my) {
        int right = left + width;
        int control = left + Math.round(width * .49F);
        int height = setting instanceof RangeSetting ? 42 : ROW;
        Gui.drawRect(left - 2, top, right + 2, top + 1, 0xFF191C22);
        if (inside(mx, my, left - 4, top, width + 8, height) && insideContent(mx, my)) hover(setting, setting.getName() + ": " + value(setting));
        if (setting instanceof BooleanSetting) control = right - 25;
        if (setting instanceof ColorSetting) control = right - 30;
        if (setting instanceof RangeSetting) control = left + width / 2;
        label(setting.getName(), left, top + 7, control - left - 7, TEXT);
        if (setting instanceof BooleanSetting) {
            boolean on = ((BooleanSetting) setting).isEnabled(); toggle(control, top + 8, on, animate(setting, on));
            contentHit(new Hit(Kind.BOOLEAN, left - 4, top, width + 8, height, setting, 0));
        } else if (setting instanceof NumberSetting) {
            NumberSetting number = (NumberSetting) setting;
            String value = compact(number.getDouble());
            int pill = Math.max(28, (int) NeverLoseFont.REGULAR.width(value) + 10);
            int end = Math.max(control + 12, right - pill - 8);
            drawTrack(control, end, top + 15, 0, fraction(number.getDouble(), number.getMinimum(), number.getMaximum()), false);
            rect(end + 6, top + 6, right - end - 6, 18, 3, CONTROL);
            label(value, end + 10, top + 8, right - end - 12, TEXT);
            contentHit(new Hit(Kind.SLIDER, control, top + 3, end - control, ROW - 3, setting, 0));
        } else if (setting instanceof RangeSetting) {
            RangeSetting range = (RangeSetting) setting;
            String value = compact(range.getMin()) + " - " + compact(range.getMax());
            label(value, control, top + 7, right - control, MUTED);
            drawTrack(left + 3, right - 3, top + 31, fraction(range.getMin(), range.getMinimum(), range.getMaximum()), fraction(range.getMax(), range.getMinimum(), range.getMaximum()), true);
            contentHit(new Hit(Kind.SLIDER, left + 3, top + 22, width - 6, 19, setting, 0));
        } else if (setting instanceof ColorSetting) {
            RenderUtils.transparencyGrid(control, top + 8, right, top + 21, 3);
            rect(control, top + 8, 30, 13, 3, ((ColorSetting) setting).getArgb());
            contentHit(new Hit(Kind.COLOR, control - 3, top + 2, 36, ROW - 3, setting, 0));
        } else {
            boolean edit = editing == setting;
            surface(control, top + 5, right - control, 20, 4, edit ? 0xFF242C3A : CONTROL);
            String value = edit ? buffer + (System.currentTimeMillis() % 1000 < 500 ? "|" : "") : value(setting);
            boolean dropdown = setting instanceof ModeSetting || setting instanceof MultiSelectSetting;
            label(value, control + 6, top + 8, right - control - (dropdown ? 21 : 11), edit ? TEXT : MUTED);
            if (dropdown) chevron(right - 12, top + 13, popup != null && popup.value == setting, MUTED);
            contentHit(new Hit(dropdown ? Kind.DROPDOWN : Kind.TEXT, control, top + 4, right - control, 22, setting, 0));
        }
    }

    private void drawTrack(int left, int right, int cy, float low, float high, boolean range) {
        rect(left, cy - 1, right - left, 3, 2, CONTROL);
        int start = left + Math.round((right - left) * low), end = left + Math.round((right - left) * high);
        rect(start, cy - 1, end - start, 3, 2, ACCENT);
        if (range) { rect(start - 5, cy - 5, 10, 10, 5, 0xFF080A0E); rect(start - 4, cy - 4, 8, 8, 4, TEXT); }
        rect(end - 5, cy - 5, 10, 10, 5, 0xFF080A0E); rect(end - 4, cy - 4, 8, 8, 4, 0xFFEDF0F6);
    }

    private float animate(Object key, boolean on) {
        Float old = switches.get(key); float next = old == null ? (on ? 1 : 0) : old + ((on ? 1 : 0) - old) * delta;
        switches.put(key, next); return next;
    }

    private void contentHit(Hit hit) {
        int top = Math.max(hit.y, contentTop), bottom = Math.min(hit.y + hit.h, contentBottom);
        if (bottom > top) hits.add(new Hit(hit.kind, hit.x, top, hit.w, bottom - top, hit.value, hit.index));
    }

    private void hover(Object key, String message) { hovered = key; tooltip = message; }
    private void drawTooltip(int mx, int my) {
        int width = Math.min(280, screenWidth - 16);
        List<String> lines = new ArrayList<String>(); String line = "";
        for (String word : tooltip.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (NeverLoseFont.REGULAR.width(next) > width - 20 && !line.isEmpty()) { lines.add(line); line = word; } else line = next;
        }
        if (!line.isEmpty()) lines.add(line);
        int height = lines.size() * 14 + 14, tx = clamp(mx + 12, 4, screenWidth - width - 4), ty = clamp(my + 17, 4, screenHeight - height - 4);
        surface(tx, ty, width, height, 5, 0xFF191D25);
        for (int i = 0; i < lines.size(); i++) label(lines.get(i), tx + 9, ty + 6 + i * 14, width - 18, TEXT);
    }

    void click(int mx, int my, int button) {
        if (button == 0 && GuiResizeGrip.contains(mx, my, x + w, y + h)) {
            release(0); resetTransient(); searching = false; resizing = true;
            resizeOffsetX = x + w - mx; resizeOffsetY = y + h - my;
            return;
        }
        if (popup != null) { clickPopup(mx, my, button); return; }
        if (color != null) {
            if (inside(mx, my, colorX, colorY, 184, 181)) { clickColor(mx, my, button); return; }
            finishEditing(); color = null; colorDrag = -1; return;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i); if (!hit.contains(mx, my)) continue;
            if (hit.kind != Kind.TEXT) finishEditing();
            if (hit.kind != Kind.SEARCH) searching = false;
            switch (hit.kind) {
                case CATEGORY:
                    if (button == 0) { category = (Category) hit.value; query = ""; scroll = 0; resetTransient(); } return;
                case CONFIG:
                    if (button == 0 && Vibe.getInstance().getConfig() != null) openPopup(hit, Vibe.getInstance().getConfig().list()); return;
                case SEARCH: if (button == 0) { searching = true; selectAll = false; } return;
                case FOLD:
                    if (button == 2) binding = (Module) hit.value;
                    else if (button == 0 || button == 1) fold((Module) hit.value); return;
                case MODULE:
                    if (button == 0) ((Module) hit.value).toggle();
                    else if (button == 1) fold((Module) hit.value);
                    else if (button == 2) binding = (Module) hit.value; return;
                case BIND: if (button == 0 || button == 2) binding = (Module) hit.value; return;
                case BOOLEAN: if (button == 0) { ((BooleanSetting) hit.value).toggle(); save(); } return;
                case SLIDER:
                    if (button == 0) {
                        slider = hit;
                        if (hit.value instanceof RangeSetting) {
                            RangeSetting range = (RangeSetting) hit.value; float pos = (mx - hit.x) / (float) hit.w;
                            upperHandle = Math.abs(pos - fraction(range.getMax(), range.getMinimum(), range.getMaximum())) <= Math.abs(pos - fraction(range.getMin(), range.getMinimum(), range.getMaximum()));
                        }
                        updateSlider(mx);
                    } return;
                case DROPDOWN:
                    if (button == 0) openPopup(hit, hit.value instanceof ModeSetting ? ((ModeSetting) hit.value).getModes() : ((MultiSelectSetting) hit.value).getOptions()); return;
                case TEXT:
                    if (button == 0) { finishEditing(); editing = (Setting<?>) hit.value; buffer = value(editing); selectAll = false; } return;
                case COLOR:
                    if (button == 0) {
                        color = (ColorSetting) hit.value; float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
                        colorX = clamp(hit.x + hit.w - 184, 4, screenWidth - 188); colorY = clamp(hit.y + hit.h + 3, 4, screenHeight - 185);
                    } return;
                case SCROLL:
                    if (button == 0) { scrollbarDragging = true; scrollbarOffset = my >= hit.index && my < hit.index + scrollbarHeight() ? my - hit.index : scrollbarHeight() / 2; dragScrollbar(my); } return;
                default: return;
            }
        }
        finishEditing(); searching = false;
        if (button == 0 && inside(mx, my, x, y, w, HEADER)) { dragging = true; dragX = mx - x; dragY = my - y; }
    }

    private void fold(Module module) {
        if (!hasVisibleSettings(module)) return;
        if (!EXPANDED.add(module.getId())) EXPANDED.remove(module.getId());
        resetTransient();
    }

    private boolean hasVisibleSettings(Module module) {
        for (Setting<?> setting : module.getSettings()) if (setting.isVisible()) return true;
        return false;
    }

    private void resetTransient() { finishEditing(); popup = null; color = null; colorDrag = -1; binding = null; slider = null; hits.clear(); }

    private void openPopup(Hit anchor, List<String> options) {
        int width = Math.max(120, anchor.w);
        for (String option : options) width = Math.max(width, Math.min(230, (int) NeverLoseFont.REGULAR.width(tr(option)) + 30));
        width = Math.min(width, screenWidth - 8);
        int height = Math.min(options.size() * 22 + 8, Math.min(228, screenHeight - 16));
        int left = clamp(anchor.x + anchor.w - width, 4, screenWidth - width - 4);
        int top = anchor.y + anchor.h + 3;
        if (top + height > screenHeight - 4) top = Math.max(4, anchor.y - height - 3);
        popup = new Popup(anchor.value, new ArrayList<String>(options), left, top, width, height);
    }

    private void drawPopup(int mx, int my) {
        Popup p = popup; surface(p.x, p.y, p.w, p.h, 5, 0xFF171C24);
        try (GuiClip clip = new GuiClip(p.x + 3, p.y + 4, p.w - 6, p.h - 8)) {
            for (int i = 0; i < p.options.size(); i++) {
                int top = p.y + 4 + i * 22 - p.scroll;
                if (top + 22 <= p.y + 4 || top >= p.y + p.h - 4) continue;
                String option = p.options.get(i);
                boolean selected = p.value instanceof ModeSetting ? ((ModeSetting) p.value).is(option) : p.value instanceof MultiSelectSetting ? ((MultiSelectSetting) p.value).isSelected(option) : Vibe.getInstance().getConfig().getActiveName().equals(option);
                if (inside(mx, my, p.x + 3, top, p.w - 6, 22) || selected) rect(p.x + 4, top, p.w - 8, 21, 3, selected ? 0xFF20334E : CONTROL);
                label(tr(option), p.x + 10, top + 4, p.w - 32, selected ? 0xFFE7F0FF : TEXT);
                if (selected) line(ACCENT, p.x + p.w - 20, top + 10, p.x + p.w - 17, top + 13, p.x + p.w - 12, top + 7);
            }
        }
        int max = Math.max(0, p.options.size() * 22 - (p.h - 8));
        if (max > 0) { int handle = Math.max(12, (p.h - 8) * (p.h - 8) / (p.options.size() * 22)); rect(p.x + p.w - 3, p.y + 4 + (p.h - 8 - handle) * p.scroll / max, 2, handle, 1, MUTED); }
    }

    private void clickPopup(int mx, int my, int button) {
        Popup p = popup;
        if (!inside(mx, my, p.x, p.y, p.w, p.h)) { popup = null; return; }
        if (button != 0 || my < p.y + 4 || my >= p.y + p.h - 4) return;
        int index = (my - p.y - 4 + p.scroll) / 22;
        if (index < 0 || index >= p.options.size()) return;
        String option = p.options.get(index);
        if (p.value instanceof MultiSelectSetting) { ((MultiSelectSetting) p.value).toggle(option); save(); }
        else {
            popup = null;
            if (p.value instanceof ModeSetting) { ((ModeSetting) p.value).setValue(option); save(); }
            else { Vibe.getInstance().getConfig().load(option, Vibe.getInstance().getModuleManager()); resetTransient(); }
        }
        hits.clear();
    }

    void wheel(int mx, int my, int amount) {
        if (amount == 0 || resizing) return;
        if (popup != null) {
            Popup p = popup;
            if (inside(mx, my, p.x, p.y, p.w, p.h)) p.scroll = clamp(p.scroll + (amount > 0 ? -44 : 44), 0, p.options.size() * 22 - p.h + 8);
            return;
        }
        if (color != null || slider != null) return;
        if (insideContent(mx, my)) { scroll = clamp(scroll + (amount > 0 ? -38 : 38), 0, maxScroll); hits.clear(); }
    }

    void release(int button) {
        if (button != 0) return;
        if (slider != null || colorDrag >= 0) save();
        dragging = false; resizing = false; scrollbarDragging = false; slider = null; colorDrag = -1;
    }

    boolean key(char character, int key) {
        boolean controlDown = Keyboard.isCreated() && GuiScreen.isCtrlKeyDown();
        if (binding != null) { binding.setKey(key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_DELETE ? Keyboard.KEY_NONE : key); binding = null; return true; }
        if (editing != null || searching) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                finishEditing(); searching = false; return true;
            }
            if (controlDown && key == Keyboard.KEY_A) { selectAll = true; return true; }
            boolean input = key == Keyboard.KEY_BACK || (controlDown && key == Keyboard.KEY_V) || ChatAllowedCharacters.isAllowedCharacter(character);
            if (!input) return true;
            String value = selectAll ? "" : searching ? query : buffer;
            selectAll = false;
            if (key == Keyboard.KEY_BACK) value = value.isEmpty() ? value : value.substring(0, value.length() - 1);
            else if (controlDown && key == Keyboard.KEY_V) value += ChatAllowedCharacters.filterAllowedCharacters(GuiScreen.getClipboardString());
            else if (ChatAllowedCharacters.isAllowedCharacter(character)) value += character;
            value = value.substring(0, Math.min(value.length(), searching ? 64 : 256));
            if (searching) { query = value; scroll = 0; hits.clear(); } else { buffer = value; commitText(); }
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE && (popup != null || color != null)) { popup = null; color = null; return true; }
        if (controlDown && key == Keyboard.KEY_F) { searching = true; popup = null; return true; }
        return false;
    }

    void close() {
        finishEditing(); release(0);
        if (screenWidth != 0) {
            savedX = x; savedY = y; savedScroll = scroll; savedCategory = category;
            savedWidth = preferredWidth; savedHeight = preferredHeight;
        }
        resetTransient();
    }

    private void commitText() {
        if (editing instanceof StringSetting) ((StringSetting) editing).setValue(buffer);
        else if (editing instanceof ColorSetting && ((ColorSetting) editing).setHex(buffer)) {
            ColorSetting value = (ColorSetting) editing;
            float[] hsv = Color.RGBtoHSB(value.getRed(), value.getGreen(), value.getBlue(), null);
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
        }
    }
    private void finishEditing() { selectAll = false; if (editing != null) { commitText(); editing = null; save(); } }

    private void updateSlider(int mx) {
        double fraction = Math.max(0, Math.min(1, (mx - slider.x) / (double) Math.max(1, slider.w)));
        if (slider.value instanceof NumberSetting) {
            NumberSetting number = (NumberSetting) slider.value;
            number.setValue(number.getMinimum() + fraction * (number.getMaximum() - number.getMinimum()));
        } else {
            RangeSetting range = (RangeSetting) slider.value;
            double value = range.getMinimum() + fraction * (range.getMaximum() - range.getMinimum());
            if (upperHandle) range.setMax(Math.max(range.getMin(), value)); else range.setMin(Math.min(range.getMax(), value));
        }
    }

    private int scrollbarHeight() { int track = contentBottom - contentTop; return Math.max(20, track * track / Math.max(1, track + maxScroll)); }
    private void dragScrollbar(int my) {
        scroll = clamp(Math.round((my - contentTop - scrollbarOffset) * maxScroll / (float) Math.max(1, contentBottom - contentTop - scrollbarHeight())), 0, maxScroll);
    }

    private void drawColor() {
        surface(colorX, colorY, 184, 181, 6, 0xFF171C24);
        label(color.getName(), colorX + 10, colorY + 7, 164, TEXT);
        int left = colorX + 10, top = colorY + 27;
        for (int iy = 0; iy < 48; iy++) for (int ix = 0; ix < 66; ix++) Gui.drawRect(left + ix * 2, top + iy * 2, left + ix * 2 + 2, top + iy * 2 + 2, 0xFF000000 | Color.HSBtoRGB(hue, ix / 65F, 1 - iy / 47F));
        for (int i = 0; i < 96; i++) Gui.drawRect(left + 140, top + i, left + 151, top + i + 1, 0xFF000000 | Color.HSBtoRGB(i / 95F, 1, 1));
        circle(left + Math.round(saturation * 131), top + Math.round((1 - brightness) * 95), 3, 0xFFFFFFFF);
        Gui.drawRect(left + 138, top + Math.round(hue * 95) - 1, left + 153, top + Math.round(hue * 95) + 1, TEXT);
        RenderUtils.transparencyGrid(left, top + 106, left + 164, top + 116, 4);
        for (int i = 0; i < 164; i++) Gui.drawRect(left + i, top + 106, left + i + 1, top + 116, RenderUtils.alpha(color.getArgb(), i * 255 / 163));
        int alphaX = left + color.getAlpha() * 163 / 255;
        Gui.drawRect(alphaX - 1, top + 105, alphaX + 1, top + 117, TEXT);
        surface(left, top + 125, 164, 21, 3, CONTROL);
        text(editing == color ? buffer + "|" : color.getHex(), left + 6, top + 129, TEXT);
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

    private boolean insideContent(int mx, int my) { return inside(mx, my, contentLeft, contentTop, contentRight - contentLeft, contentBottom - contentTop); }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
    private static float fraction(double value, double min, double max) { return max <= min ? 0 : (float) Math.max(0, Math.min(1, (value - min) / (max - min))); }
    private static String compact(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static String tr(String text) { return LanguageManager.translate(text); }
    private static void save() { if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager()); }
    private static String value(Setting<?> setting) {
        if (setting instanceof NumberSetting) return compact(((NumberSetting) setting).getDouble());
        if (setting instanceof RangeSetting) { RangeSetting r = (RangeSetting) setting; return compact(r.getMin()) + " - " + compact(r.getMax()); }
        if (setting instanceof MultiSelectSetting) {
            StringBuilder result = new StringBuilder();
            for (String option : ((MultiSelectSetting) setting).getValue()) { if (result.length() > 0) result.append(", "); result.append(tr(option)); }
            return result.length() == 0 ? tr("None") : result.toString();
        }
        if (setting instanceof ColorSetting) return ((ColorSetting) setting).getHex();
        if (setting instanceof BooleanSetting) return tr(((BooleanSetting) setting).isEnabled() ? "Enabled" : "Disabled");
        return setting instanceof ModeSetting ? tr(String.valueOf(setting.getValue())) : String.valueOf(setting.getValue());
    }

    private enum Kind { CATEGORY, CONFIG, SEARCH, FOLD, MODULE, BIND, BOOLEAN, SLIDER, DROPDOWN, TEXT, COLOR, SCROLL }
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
