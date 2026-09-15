package dev.vibe.ui;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** A small live colour-preset picker. */
public final class MenuThemesGui extends GuiScreen {
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private int left, top, panelWidth, panelHeight;
    private boolean saveFailed;

    public MenuThemesGui(GuiScreen parent, MainMenuShaderManager shaders) { this.parent = parent; this.shaders = shaders; }

    @Override public void initGui() {
        panelWidth = Math.min(420, width - 24);
        panelHeight = Math.min(282, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        buttonList.clear();
        buttonList.add(new AccountScreenStyle.Button(0, left + panelWidth - 64, top + 14, 48, 24, "Back", "", false, false));
        int rowHeight = (panelHeight - 86) / 3;
        int cellWidth = (panelWidth - 40) / 2;
        for (MenuThemes.Preset preset : MenuThemes.Preset.values()) {
            int index = preset.ordinal();
            buttonList.add(new PresetButton(10 + index, left + 16 + index % 2 * (cellWidth + 8),
                    top + 58 + index / 2 * rowHeight, cellWidth, rowHeight - 8, preset));
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        drawRect(0, 0, width, height, 0x70000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title("Themes", left + 16, top + 15);
        AccountScreenStyle.text("Choose your colours", left + 16, top + 35, AccountScreenStyle.MUTED);
        AccountScreenStyle.text(saveFailed ? "Couldn't save. Try again." : "Applies to menus & accounts", left + 16,
                top + panelHeight - 18, saveFailed ? AccountScreenStyle.ERROR : AccountScreenStyle.MUTED);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) mc.displayGuiScreen(parent);
        else if (button.id >= 10 && button.id < 10 + MenuThemes.Preset.values().length)
            saveFailed = !MenuThemes.select(MenuThemes.Preset.values()[button.id - 10]);
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else super.keyTyped(character, key);
    }

    private static final class PresetButton extends GuiButton {
        private final MenuThemes.Preset preset;
        PresetButton(int id, int x, int y, int width, int height, MenuThemes.Preset preset) {
            super(id, x, y, width, height, preset.label); this.preset = preset;
        }
        @Override public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            boolean selected = MenuThemes.current() == preset;
            AccountScreenStyle.panel(xPosition, yPosition, width, height, preset.surface,
                    selected || hovered ? preset.accent : AccountScreenStyle.BORDER);
            MenuRoundedRenderer.rect(xPosition + 10, yPosition + (height - 16) / 2, 16, 16, 8, preset.accent);
            AccountScreenStyle.text(displayString, xPosition + 34, yPosition + (height - 8) / 2, AccountScreenStyle.TEXT);
            if (selected) AccountScreenStyle.text("*", xPosition + width - 14, yPosition + (height - 8) / 2, preset.accent);
        }
    }
}
