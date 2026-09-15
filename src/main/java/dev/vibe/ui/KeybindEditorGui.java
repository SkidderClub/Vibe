package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.Module;
import dev.vibe.module.impl.BlurModule;
import dev.vibe.module.impl.KeybindEditorModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Keyboard-layout-aware bind editor. Pick a physical key first, then click
 * module rows to add/remove them from that key. Multiple modules may share a
 * key; every occupied key is visibly marked on the keyboard.
 */
public final class KeybindEditorGui extends GuiScreen {
    private final KeybindEditorModule module;
    private final ParticlesRenderer particles = new ParticlesRenderer();
    private final List<Module> modules = new ArrayList<Module>();
    private final List<KeyCell> keyCells = new ArrayList<KeyCell>();
    private int selectedKey = Keyboard.KEY_NONE;
    private int left, top, panelWidth, moduleScroll;

    private static final int[][] ROWS = {
            {Keyboard.KEY_ESCAPE, Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5, Keyboard.KEY_F6, Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10, Keyboard.KEY_F11, Keyboard.KEY_F12},
            {Keyboard.KEY_GRAVE, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4, Keyboard.KEY_5, Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9, Keyboard.KEY_0, Keyboard.KEY_MINUS, Keyboard.KEY_EQUALS, Keyboard.KEY_BACK},
            {Keyboard.KEY_TAB, Keyboard.KEY_Q, Keyboard.KEY_W, Keyboard.KEY_E, Keyboard.KEY_R, Keyboard.KEY_T, Keyboard.KEY_Y, Keyboard.KEY_U, Keyboard.KEY_I, Keyboard.KEY_O, Keyboard.KEY_P, Keyboard.KEY_LBRACKET, Keyboard.KEY_RBRACKET, Keyboard.KEY_BACKSLASH},
            {Keyboard.KEY_CAPITAL, Keyboard.KEY_A, Keyboard.KEY_S, Keyboard.KEY_D, Keyboard.KEY_F, Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_J, Keyboard.KEY_K, Keyboard.KEY_L, Keyboard.KEY_SEMICOLON, Keyboard.KEY_APOSTROPHE, Keyboard.KEY_RETURN},
            {Keyboard.KEY_LSHIFT, Keyboard.KEY_Z, Keyboard.KEY_X, Keyboard.KEY_C, Keyboard.KEY_V, Keyboard.KEY_B, Keyboard.KEY_N, Keyboard.KEY_M, Keyboard.KEY_COMMA, Keyboard.KEY_PERIOD, Keyboard.KEY_SLASH, Keyboard.KEY_RSHIFT},
            {Keyboard.KEY_LCONTROL, Keyboard.KEY_LMENU, Keyboard.KEY_SPACE, Keyboard.KEY_RMENU, Keyboard.KEY_RCONTROL}
    };

    public KeybindEditorGui(KeybindEditorModule module) { this.module = module; }

    @Override public void initGui() {
        panelWidth = Math.max(500, Math.min(820, width - 16));
        left = Math.max(8, (width - panelWidth) / 2);
        top = Math.max(14, (height - 390) / 2);
        modules.clear();
        modules.addAll(Vibe.getInstance().getModuleManager().getModules());
        for (Module value : modules) if (value.getKey() != Keyboard.KEY_NONE) { selectedKey = value.getKey(); break; }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int panelHeight = Math.min(390, height - 28);
        SkeetEditorStyle.backdrop(this, BlurModule.KEYBIND_EDITOR, partialTicks);
        SkeetEditorStyle.window(left, top, left + panelWidth, top + panelHeight, "Keybind editor", "select a key • click modules to add or remove");
        drawModules(panelHeight);
        drawKeyboard(panelHeight, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawModules(int panelHeight) {
        int listLeft = left + 12, listTop = top + 43, listRight = left + 218, listBottom = top + panelHeight - 13;
        SkeetEditorStyle.panel(listLeft, listTop, listRight, listBottom, "Modules");
        int y = listTop + 23;
        int visibleRows = Math.max(1, (listBottom - y - 3) / 20);
        moduleScroll = Math.max(0, Math.min(Math.max(0, modules.size() - visibleRows), moduleScroll));
        for (int index = moduleScroll; index < modules.size() && y + 18 <= listBottom; index++) {
            Module value = modules.get(index);
            boolean onSelectedKey = selectedKey != Keyboard.KEY_NONE && value.getKey() == selectedKey;
            boolean hasOtherKey = value.getKey() != Keyboard.KEY_NONE && !onSelectedKey;
            SkeetEditorStyle.row(listLeft + 3, y, listRight - 3, y + 18, onSelectedKey, hasOtherKey);
            String status = onSelectedKey ? "[x]" : hasOtherKey ? "[" + compactLabel(value.getKey()) + "]" : "[ ]";
            int statusWidth = fontRendererObj.getStringWidth(status);
            fontRendererObj.drawStringWithShadow(trim(value.getName(), listRight - listLeft - 20 - statusWidth), listLeft + 9, y + 5, onSelectedKey ? SkeetEditorStyle.accent(0.1F) : SkeetEditorStyle.TEXT);
            fontRendererObj.drawStringWithShadow(status, listRight - 9 - statusWidth, y + 5, onSelectedKey ? SkeetEditorStyle.accent(0.1F) : SkeetEditorStyle.MUTED);
            y += 20;
        }
        if (modules.size() > visibleRows) {
            int trackHeight = listBottom - listTop - 28, thumb = Math.max(12, trackHeight * visibleRows / modules.size());
            int range = Math.max(1, modules.size() - visibleRows), thumbY = listTop + 24 + (trackHeight - thumb) * moduleScroll / range;
            Gui.drawRect(listRight - 5, thumbY, listRight - 3, thumbY + thumb, SkeetEditorStyle.accent(0.2F));
        }
    }

    private void drawKeyboard(int panelHeight, int mouseX, int mouseY) {
        int keyboardLeft = left + 232, keyboardRight = left + panelWidth - 14;
        String title = selectedKey == Keyboard.KEY_NONE ? "Choose a key" : compactLabel(selectedKey) + "  •  " + boundCount(selectedKey) + " module(s)";
        SkeetEditorStyle.panel(keyboardLeft - 3, top + 42, keyboardRight + 3, top + panelHeight - 13, "Keyboard");
        // Leave the shared panel caption alone and place the selected-key
        // summary in the content region underneath it.
        fontRendererObj.drawStringWithShadow(title, keyboardLeft, top + 63, selectedKey == Keyboard.KEY_NONE ? SkeetEditorStyle.MUTED : SkeetEditorStyle.accent(0.1F));
        if (selectedKey != Keyboard.KEY_NONE) fontRendererObj.drawStringWithShadow(trim(boundNames(selectedKey), keyboardRight - keyboardLeft), keyboardLeft, top + 75, SkeetEditorStyle.MUTED);
        keyCells.clear();
        int y = top + 93;
        for (int[] row : ROWS) {
            int total = 0; for (int key : row) total += keyWidth(key) + 3;
            int x = keyboardLeft + Math.max(0, (keyboardRight - keyboardLeft - total) / 2);
            for (int key : row) {
                int keyWidth = keyWidth(key), count = boundCount(key);
                boolean picked = key == selectedKey;
                SkeetEditorStyle.row(x, y, x + keyWidth, y + 25, picked, count > 0 && !picked);
                if (count > 0 && !picked) SkeetEditorStyle.border(x, y, x + keyWidth, y + 25, SkeetEditorStyle.accent(0.4F));
                String label = compactLabel(key);
                fontRendererObj.drawStringWithShadow(label, x + (keyWidth - fontRendererObj.getStringWidth(label)) / 2, y + 9, picked ? 0xFF101012 : SkeetEditorStyle.TEXT);
                keyCells.add(new KeyCell(key, x, y, keyWidth, 25));
                x += keyWidth + 3;
            }
            y += 34;
        }
        if (selectedKey != Keyboard.KEY_NONE) button(keyboardLeft, Math.min(top + panelHeight - 31, y + 12), "UNBIND ALL", 0xFFFF6A82);
        for (KeyCell cell : keyCells) if (cell.contains(mouseX, mouseY) && boundCount(cell.key) > 0) { drawHoveringText(java.util.Collections.singletonList(trim(boundNames(cell.key), 220)), mouseX, mouseY); break; }
    }

    private void button(int x, int y, String text, int color) { int w = fontRendererObj.getStringWidth(text) + 16; SkeetEditorStyle.button(x, y, x + w, y + 20, text, color != 0xFFFF6A82); }
    private boolean in(int x, int y, int w, int h, int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private int boundCount(int key) { int count = 0; for (Module value : modules) if (value.getKey() == key) count++; return count; }
    private String boundNames(int key) { StringBuilder names = new StringBuilder(); for (Module value : modules) if (value.getKey() == key) { if (names.length() > 0) names.append(", "); names.append(value.getName()); } return names.length() == 0 ? "No modules bound" : names.toString(); }
    private int keyWidth(int key) { return key == Keyboard.KEY_SPACE ? 118 : Math.max(27, Math.min(48, fontRendererObj.getStringWidth(compactLabel(key)) + 10)); }
    private String trim(String value, int maxWidth) { return fontRendererObj.trimStringToWidth(value, Math.max(0, maxWidth)); }

    private String compactLabel(int key) {
        switch (key) {
            case Keyboard.KEY_ESCAPE: return "ESC"; case Keyboard.KEY_BACK: return "BKSP"; case Keyboard.KEY_TAB: return "TAB"; case Keyboard.KEY_CAPITAL: return "CAPS"; case Keyboard.KEY_RETURN: return "ENTER";
            case Keyboard.KEY_LSHIFT: case Keyboard.KEY_RSHIFT: return "SHIFT"; case Keyboard.KEY_LCONTROL: case Keyboard.KEY_RCONTROL: return "CTRL"; case Keyboard.KEY_LMENU: case Keyboard.KEY_RMENU: return "ALT";
            case Keyboard.KEY_GRAVE: return "`"; case Keyboard.KEY_MINUS: return "-"; case Keyboard.KEY_EQUALS: return "="; case Keyboard.KEY_LBRACKET: return "["; case Keyboard.KEY_RBRACKET: return "]";
            case Keyboard.KEY_BACKSLASH: return "\\"; case Keyboard.KEY_SEMICOLON: return ";"; case Keyboard.KEY_APOSTROPHE: return "'"; case Keyboard.KEY_COMMA: return ","; case Keyboard.KEY_PERIOD: return "."; case Keyboard.KEY_SLASH: return "/";
            default: String value = Keyboard.getKeyName(key); return value == null ? "?" : value;
        }
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) { super.mouseClicked(mouseX, mouseY, mouseButton); return; }
        for (KeyCell cell : keyCells) if (cell.contains(mouseX, mouseY)) { selectedKey = cell.key; return; }
        int y = top + 66, listBottom = top + Math.min(390, height - 28) - 13;
        for (int index = moduleScroll; index < modules.size() && y + 18 <= listBottom; index++) {
            if (in(left + 15, y, 200, 18, mouseX, mouseY) && selectedKey != Keyboard.KEY_NONE) { Module value = modules.get(index); value.setKey(value.getKey() == selectedKey ? Keyboard.KEY_NONE : selectedKey); return; }
            y += 20;
        }
        int keyboardLeft = left + 232, unbindY = Math.min(top + Math.min(390, height - 28) - 31, top + 93 + ROWS.length * 34 + 12);
        if (selectedKey != Keyboard.KEY_NONE && in(keyboardLeft, unbindY, fontRendererObj.getStringWidth("UNBIND ALL") + 16, 20, mouseX, mouseY)) { for (Module value : modules) if (value.getKey() == selectedKey) value.setKey(Keyboard.KEY_NONE); return; }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth), wheel = Mouse.getEventDWheel();
        if (wheel != 0 && mouseX >= left && mouseX <= left + 220) moduleScroll += wheel < 0 ? 2 : -2;
    }
    @Override protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(null); return; } super.keyTyped(typedChar, keyCode); }
    @Override public void onGuiClosed() { if (module.isEnabled()) module.setEnabled(false); super.onGuiClosed(); }
    @Override public boolean doesGuiPauseGame() { return false; }

    private static final class KeyCell {
        private final int key, x, y, width, height;
        private KeyCell(int key, int x, int y, int width, int height) { this.key = key; this.x = x; this.y = y; this.width = width; this.height = height; }
        private boolean contains(int mouseX, int mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    }
}
