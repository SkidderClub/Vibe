package dev.vibe.ui.menu;

import dev.vibe.language.LanguageManager;
import dev.vibe.ui.GuiClip;
import dev.vibe.ui.GuiRenderState;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Scrollable language picker shared by setup and the main menu. */
public final class LanguagePickerGui extends GuiScreen {
    private final GuiScreen parent;
    private final MainMenuShaderManager shaders;
    private int left, top, panelWidth, panelHeight, gridTop, gridBottom, columns, tileWidth;
    private int scroll;

    public LanguagePickerGui(GuiScreen parent, MainMenuShaderManager shaders) {
        this.parent = parent;
        this.shaders = shaders;
    }

    @Override
    public void initGui() {
        panelWidth = Math.min(610, width - 24);
        panelHeight = Math.min(390, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        columns = panelWidth >= 490 ? 3 : 2;
        tileWidth = (panelWidth - 32 - (columns - 1) * 7) / columns;
        gridTop = top + 54;
        gridBottom = top + panelHeight - 15;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (shaders != null) shaders.draw(width, height); else drawDefaultBackground();
        GuiRenderState.prepare(false);
        drawRect(0, 0, width, height, 0x8A000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title(dev.vibe.language.LanguageManager.translate("Language selector"), left + 16, top + 15);
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(LanguageManager.translate("Choose language"), panelWidth - 64),
                left + 16, top + 35, AccountScreenStyle.MUTED);

        List<String> languages = LanguageManager.languages();
        int tileHeight = 27;
        try (GuiClip clip = new GuiClip(left + 15, gridTop, panelWidth - 30, gridBottom - gridTop)) {
            for (int index = 0; index < languages.size(); index++) {
                String language = languages.get(index);
                int column = index % columns;
                int row = index / columns;
                int x = left + 16 + column * (tileWidth + 7);
                int y = gridTop + row * (tileHeight + 5) - scroll;
                if (y + tileHeight < gridTop || y > gridBottom) continue;
                boolean hovered = mouseX >= x && mouseX < x + tileWidth && mouseY >= y && mouseY < y + tileHeight;
                boolean selected = language.equals(LanguageManager.selectedLanguage());
                AccountScreenStyle.panel(x, y, tileWidth, tileHeight,
                        hovered ? AccountScreenStyle.HOVER : selected ? AccountScreenStyle.TINT : AccountScreenStyle.SURFACE,
                        hovered || selected ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
                LanguageFlags.draw(language, x + 7, y + 5);
                String name = AccountScreenStyle.fitRaw(LanguageManager.displayName(language), tileWidth - 42);
                AccountScreenStyle.rawText(name, x + 37, y + 10, selected ? AccountScreenStyle.TEXT : AccountScreenStyle.MUTED);
            }
        }
        if (maxScroll() > 0) {
            int trackHeight = gridBottom - gridTop;
            int thumb = Math.max(22, trackHeight * trackHeight / Math.max(trackHeight, contentHeight()));
            int travel = trackHeight - thumb;
            int y = gridTop + (maxScroll() == 0 ? 0 : scroll * travel / maxScroll());
            drawRect(left + panelWidth - 12, gridTop, left + panelWidth - 9, gridBottom, 0x55000000);
            drawRect(left + panelWidth - 12, y, left + panelWidth - 9, y + thumb, AccountScreenStyle.ACCENT);
        }
        AccountScreenStyle.rawText(dev.vibe.language.LanguageManager.translate("Esc"), left + panelWidth - 39, top + 36, AccountScreenStyle.MUTED);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || mouseY < gridTop || mouseY >= gridBottom) return;
        List<String> languages = LanguageManager.languages();
        int tileHeight = 27;
        for (int index = 0; index < languages.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            int x = left + 16 + column * (tileWidth + 7);
            int y = gridTop + row * (tileHeight + 5) - scroll;
            if (mouseX >= x && mouseX < x + tileWidth && mouseY >= y && mouseY < y + tileHeight) {
                LanguageSelector.select(languages.get(index));
                mc.displayGuiScreen(parent);
                return;
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int mouseY = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (mouseX >= left && mouseX < left + panelWidth && mouseY >= gridTop && mouseY < gridBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (wheel < 0 ? 32 : -32)));
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
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private int contentHeight() {
        return ((LanguageManager.languages().size() + columns - 1) / columns) * 32 - 5;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (gridBottom - gridTop));
    }
}
