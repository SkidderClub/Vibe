package dev.vibe.ui;

import dev.vibe.account.AccountManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

/** Local profiles are explicitly separate from authenticated Microsoft accounts. */
final class OfflineAccountGui extends GuiScreen {
    private final AccountManagerGui parent;
    private final MainMenuShaderManager shaders;
    private final AccountManager accounts;
    private GuiTextField name;
    private int left, top, cardWidth;

    OfflineAccountGui(AccountManagerGui parent, MainMenuShaderManager shaders, AccountManager accounts) {
        this.parent = parent;
        this.shaders = shaders;
        this.accounts = accounts;
    }

    @Override public void initGui() {
        String value = name == null ? "" : name.getText();
        Keyboard.enableRepeatEvents(true);
        cardWidth = Math.min(360, width - 24);
        left = (width - cardWidth) / 2;
        top = (height - 184) / 2;
        name = new GuiTextField(0, fontRendererObj, left + 27, top + 87, cardWidth - 54, 14);
        name.setEnableBackgroundDrawing(false);
        name.setTextColor(AccountScreenStyle.TEXT);
        name.setMaxStringLength(16);
        name.setFocused(true);
        name.setText(value);
        buttonList.clear();
        int buttonWidth = (cardWidth - 40) / 2;
        buttonList.add(new AccountScreenStyle.Button(1, left + 16, top + 140, buttonWidth, 28, "Add and use", "", true, false));
        buttonList.add(new AccountScreenStyle.Button(2, left + 24 + buttonWidth, top + 140, buttonWidth, 28, "Back", "", false, false));
    }

    @Override public void drawScreen(int x, int y, float ticks) {
        shaders.draw(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, cardWidth, 184);
        AccountScreenStyle.title("Offline profile", left + 16, top + 17);
        AccountScreenStyle.text("For singleplayer and offline servers.", left + 16, top + 39, AccountScreenStyle.MUTED);
        AccountScreenStyle.text("USERNAME", left + 16, top + 64, AccountScreenStyle.MUTED);
        AccountScreenStyle.panel(left + 16, top + 79, cardWidth - 32, 29, AccountScreenStyle.SURFACE,
                name.isFocused() ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
        name.drawTextBox();
        if (!name.getText().isEmpty() && !validName())
            AccountScreenStyle.text("Use letters, numbers or underscores.", left + 16, top + 117, AccountScreenStyle.ERROR);
        buttonList.get(0).enabled = validName() && accounts.isStorageAvailable() && !accounts.isBusy();
        super.drawScreen(x, y, ticks);
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1 && validName() && accounts.isStorageAvailable() && !accounts.isBusy()) {
            accounts.addOffline(name.getText());
            parent.showAccounts();
        } else if (button.id == 2) mc.displayGuiScreen(parent);
    }

    private boolean validName() { return name.getText().matches("[A-Za-z0-9_]{1,16}"); }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) actionPerformed(buttonList.get(0));
        else name.textboxKeyTyped(character, key);
    }

    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        name.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }

    @Override public void updateScreen() { name.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
}
