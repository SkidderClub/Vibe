package dev.vibe.ui;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Small preset browser opened from the Forge-injected main-menu button. */
public final class ShaderMenuGui extends GuiScreen {
    private static final int PRESET_ID = 3000;
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private static final int COLUMNS = 2;
    private static final int ROW_HEIGHT = 28;
    private int left, top, panelWidth, panelHeight, listTop, columnWidth;
    private int scrollRow;
    private int visibleRows;

    public ShaderMenuGui(GuiScreen parent, MainMenuShaderManager shaders) {
        this.parent = parent;
        this.shaders = shaders;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        List<String> presets = shaders.getPresets();
        panelWidth = Math.min(480, width - 24);
        panelHeight = Math.min(420, height - 20);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        listTop = top + 72;
        columnWidth = (panelWidth - 38) / COLUMNS;
        visibleRows = Math.max(1, (panelHeight - 125) / ROW_HEIGHT);
        clampScroll(presets);
        int start = scrollRow * COLUMNS;
        int visible = Math.min(presets.size() - start, visibleRows * COLUMNS);
        for (int slot = 0; slot < visible; slot++) {
            String preset = presets.get(start + slot);
            int column = slot % COLUMNS;
            int row = slot / COLUMNS;
            int buttonLeft = left + 14 + column * (columnWidth + 6);
            String label = preset;
            buttonList.add(new AccountScreenStyle.Button(PRESET_ID + slot, buttonLeft, listTop + row * ROW_HEIGHT, columnWidth, 24, label, "", preset.equals(shaders.getSelected()), false));
        }
        int buttonWidth = (panelWidth - 40) / 3;
        int footer = top + panelHeight - 38;
        buttonList.add(new AccountScreenStyle.Button(1, left + 14, footer, buttonWidth, 25, shaders.isEnabled() ? "Disable shader" : "Enable shader", "", true, false));
        buttonList.add(new AccountScreenStyle.Button(2, left + 20 + buttonWidth, footer, buttonWidth, 25, "Open folder", "", false, false));
        buttonList.add(new AccountScreenStyle.Button(5, left + 26 + buttonWidth * 2, footer, buttonWidth, 25, "Back", "", false, false));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        List<String> presets = shaders.getPresets();
        if (button.id >= PRESET_ID) {
            int index = scrollRow * COLUMNS + button.id - PRESET_ID;
            if (index < presets.size()) {
                shaders.select(presets.get(index));
                initGui();
            }
            return;
        }
        if (button.id == 1) {
            shaders.setEnabled(!shaders.isEnabled());
            initGui();
        } else if (button.id == 2) {
            shaders.openFolder();
        } else if (button.id == 5) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title("Shaders", left + 14, top + 14);
        AccountScreenStyle.text(dev.vibe.language.LanguageManager.translate("Active shader") + ": " + shaders.getSelected(), left + 14, top + 39, AccountScreenStyle.ACCENT);
        AccountScreenStyle.text("Scroll to browse shaders", left + 14, top + 55, AccountScreenStyle.MUTED);
        drawScrollBar();
        if (!shaders.getLastError().isEmpty()) {
            AccountScreenStyle.text(AccountScreenStyle.fit(dev.vibe.language.LanguageManager.translate("Shader unavailable on this OpenGL profile"), panelWidth - 28), left + 14, top + panelHeight - 51, AccountScreenStyle.ERROR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            List<String> presets = shaders.getPresets();
            scrollRow += wheel > 0 ? -1 : 1;
            clampScroll(presets);
            initGui();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void clampScroll(List<String> presets) {
        int totalRows = (presets.size() + COLUMNS - 1) / COLUMNS;
        scrollRow = Math.max(0, Math.min(Math.max(0, totalRows - visibleRows), scrollRow));
    }

    private void drawScrollBar() {
        List<String> presets = shaders.getPresets();
        int totalRows = (presets.size() + COLUMNS - 1) / COLUMNS;
        if (totalRows <= visibleRows) return;
        int top = listTop;
        int bottom = top + visibleRows * ROW_HEIGHT - 1;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(12, Math.round(trackHeight * (visibleRows / (float) totalRows)));
        int travel = trackHeight - thumbHeight;
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbTop = top + Math.round(travel * (scrollRow / (float) maxScroll));
        Gui.drawRect(left + panelWidth - 10, top, left + panelWidth - 8, bottom, AccountScreenStyle.BORDER);
        Gui.drawRect(left + panelWidth - 10, thumbTop, left + panelWidth - 8, thumbTop + thumbHeight, AccountScreenStyle.ACCENT);
    }

    private String compact(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(1, length - 1)) + "…";
    }
}
