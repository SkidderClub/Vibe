package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.module.Category;
import dev.vibe.module.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.lwjgl.input.Keyboard;

/**
 * A deliberately clean, white category grid based on the classic Sigma
 * ClickGUI.  Each category owns its own scroll position, so a long category
 * never steals room from the rest of the workspace.
 */
final class SigmaWorkspace {
    private static final int HEADER = 22;
    private static final int ROW = 14;
    private final Minecraft minecraft = Minecraft.getMinecraft();
    private final int[] scroll = new int[Category.values().length];
    private final int[] maximum = new int[Category.values().length];
    private final Panel[] panels = new Panel[Category.values().length];
    private Module binding;

    void draw(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        GuiRenderState.prepare(false);
        Gui.drawRect(0, 0, screenWidth, screenHeight, 0x55000000);

        int columns = screenWidth >= 560 ? 4 : screenWidth >= 410 ? 3 : 2;
        int rows = (Category.values().length + columns - 1) / columns;
        int workspaceWidth = Math.min(screenWidth - 18, columns * 170 + (columns - 1) * 8);
        int workspaceHeight = Math.min(screenHeight - 26, Math.max(190, rows * 156 + (rows - 1) * 8));
        int left = (screenWidth - workspaceWidth) / 2;
        int top = (screenHeight - workspaceHeight) / 2;
        int panelWidth = (workspaceWidth - (columns - 1) * 8) / columns;
        int panelHeight = (workspaceHeight - (rows - 1) * 8) / rows;

        Gui.drawRect(left - 2, top - 2, left + workspaceWidth + 2, top + workspaceHeight + 2, 0xFFA8A8A8);
        Gui.drawRect(left, top, left + workspaceWidth, top + workspaceHeight, 0xFFFFFFFF);

        for (int index = 0; index < Category.values().length; index++) {
            Category category = Category.values()[index];
            int column = index % columns;
            int row = index / columns;
            int x = left + column * (panelWidth + 8);
            int y = top + row * (panelHeight + 8);
            drawPanel(category, index, x, y, panelWidth, panelHeight, mouseX, mouseY);
        }
        if (binding != null) {
            String message = "Press a key for " + binding.getName() + " (Esc clears)";
            int width = minecraft.fontRendererObj.getStringWidth(message) + 16;
            int x = (screenWidth - width) / 2;
            Gui.drawRect(x - 1, top - 24, x + width + 1, top - 4, 0xFF969696);
            Gui.drawRect(x, top - 23, x + width, top - 5, 0xFFFFFFFF);
            minecraft.fontRendererObj.drawString(message, x + 8, top - 18, 0xFF222222);
        }
    }

    private void drawPanel(Category category, int index, int x, int y, int width, int height, int mouseX, int mouseY) {
        int right = x + width;
        int bottom = y + height;
        Gui.drawRect(x - 1, y - 1, right + 1, bottom + 1, 0xFFB8B8B8);
        Gui.drawRect(x, y, right, y + HEADER, 0xFFEDEDED);
        Gui.drawRect(x, y + HEADER, right, bottom, 0xFFFFFFFF);
        minecraft.fontRendererObj.drawString(category.getLabel(), x + 8, y + 7, 0xFF858585);

        List<Module> modules = Vibe.getInstance().getModuleManager().getModules(category);
        int listTop = y + HEADER + 3;
        int listBottom = bottom - 5;
        int viewport = Math.max(1, listBottom - listTop);
        maximum[index] = Math.max(0, modules.size() * ROW - viewport);
        scroll[index] = clamp(scroll[index], 0, maximum[index]);
        panels[index] = new Panel(x, listTop, right, listBottom, category);

        try (GuiClip clip = new GuiClip(x + 1, listTop, width - 2, viewport)) {
            for (int row = 0; row < modules.size(); row++) {
                Module module = modules.get(row);
                int rowY = listTop + row * ROW - scroll[index];
                if (rowY + ROW <= listTop || rowY >= listBottom) continue;
                boolean hover = inside(mouseX, mouseY, x + 1, rowY, width - 2, ROW);
                if (module.isEnabled()) {
                    Gui.drawRect(x + 1, rowY, right - 1, rowY + ROW, 0xFF00A8E8);
                } else if (hover) {
                    Gui.drawRect(x + 1, rowY, right - 1, rowY + ROW, 0xFFF0F0F0);
                }
                String label = binding == module ? "Press a key..." : module.getName();
                int colour = module.isEnabled() ? 0xFFFFFFFF : 0xFF3C3C3C;
                minecraft.fontRendererObj.drawString(minecraft.fontRendererObj.trimStringToWidth(label, width - 12), x + 7, rowY + 3, colour);
            }
        }
        if (maximum[index] > 0) {
            int track = viewport;
            int thumb = Math.max(12, viewport * viewport / (viewport + maximum[index]));
            int thumbY = listTop + Math.round((track - thumb) * scroll[index] / (float) maximum[index]);
            Gui.drawRect(right - 3, listTop, right - 2, listBottom, 0xFFE0E0E0);
            Gui.drawRect(right - 3, thumbY, right - 2, thumbY + thumb, 0xFF00A8E8);
        }
    }

    void click(int mouseX, int mouseY, int button) {
        if (binding != null) return;
        for (int index = 0; index < panels.length; index++) {
            Panel panel = panels[index];
            if (panel == null || !inside(mouseX, mouseY, panel.left, panel.top, panel.right - panel.left, panel.bottom - panel.top)) continue;
            int row = (mouseY - panel.top + scroll[index]) / ROW;
            List<Module> modules = Vibe.getInstance().getModuleManager().getModules(panel.category);
            if (row < 0 || row >= modules.size()) return;
            Module module = modules.get(row);
            if (button == 2) {
                binding = module;
            } else if (button == 0) {
                module.toggle();
                save();
            }
            return;
        }
    }

    void wheel(int mouseX, int mouseY, int amount) {
        for (int index = 0; index < panels.length; index++) {
            Panel panel = panels[index];
            if (panel != null && inside(mouseX, mouseY, panel.left, panel.top, panel.right - panel.left, panel.bottom - panel.top)) {
                scroll[index] = clamp(scroll[index] + (amount > 0 ? -24 : 24), 0, maximum[index]);
                return;
            }
        }
    }

    boolean key(char typedChar, int keyCode) {
        if (binding == null) return false;
        binding.setKey(keyCode == Keyboard.KEY_ESCAPE ? Keyboard.KEY_NONE : keyCode);
        binding = null;
        save();
        return true;
    }

    void close() { binding = null; }

    private static void save() {
        if (Vibe.getInstance().getConfig() != null) Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(Math.max(min, max), value)); }
    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }

    private static final class Panel {
        final int left, top, right, bottom; final Category category;
        Panel(int left, int top, int right, int bottom, Category category) { this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.category = category; }
    }
}
