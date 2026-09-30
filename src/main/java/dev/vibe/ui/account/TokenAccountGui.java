package dev.vibe.ui.account;

import dev.vibe.account.AccountManager;
import dev.vibe.account.MicrosoftRefreshToken;
import dev.vibe.account.MinecraftToken;
import dev.vibe.ui.menu.AccountScreenStyle;
import dev.vibe.ui.menu.MainMenuShaderManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

/** Adds an account from a pasted Minecraft access token or Microsoft refresh token. Tokens are only pasted, never shown in full. */
final class TokenAccountGui extends GuiScreen {
    private final AccountManagerGui parent;
    private final MainMenuShaderManager shaders;
    private final AccountManager accounts;
    private MinecraftToken token;
    private MicrosoftRefreshToken refresh;
    private boolean rejected;
    private int left, top, cardWidth;

    TokenAccountGui(AccountManagerGui parent, MainMenuShaderManager shaders, AccountManager accounts) {
        this.parent = parent;
        this.shaders = shaders;
        this.accounts = accounts;
    }

    @Override public void initGui() {
        cardWidth = Math.min(360, width - 24);
        left = (width - cardWidth) / 2;
        top = (height - 184) / 2;
        buttonList.clear();
        int buttonWidth = (cardWidth - 48) / 3;
        buttonList.add(new AccountScreenStyle.Button(1, left + 16, top + 140, buttonWidth, 28, "Add and use", "", true, false));
        buttonList.add(new AccountScreenStyle.Button(3, left + 24 + buttonWidth, top + 140, buttonWidth, 28, "Paste", "", false, false));
        buttonList.add(new AccountScreenStyle.Button(2, left + 32 + 2 * buttonWidth, top + 140, cardWidth - 48 - 2 * buttonWidth, 28, "Back", "", false, false));
    }

    @Override public void drawScreen(int x, int y, float ticks) {
        shaders.draw(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.color(1, 1, 1, 1);
        drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, cardWidth, 184);
        AccountScreenStyle.title("Token login", left + 16, top + 17);
        AccountScreenStyle.text(AccountScreenStyle.fit("Access token (eyJra..., about a day) or refresh token (M.C...).", cardWidth - 32),
                left + 16, top + 39, AccountScreenStyle.MUTED);
        AccountScreenStyle.text("TOKEN", left + 16, top + 64, AccountScreenStyle.MUTED);
        AccountScreenStyle.panel(left + 16, top + 79, cardWidth - 32, 29, AccountScreenStyle.SURFACE,
                token != null || refresh != null ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
        AccountScreenStyle.text(AccountScreenStyle.fit(token != null ? token.preview() : refresh != null ? refresh.preview() : "Press Ctrl+V or Paste", cardWidth - 54),
                left + 27, top + 90, token != null || refresh != null ? AccountScreenStyle.TEXT : AccountScreenStyle.MUTED);
        String note = rejected ? "The clipboard does not contain an access or refresh token."
                : refresh != null ? "Refresh token: stays signed in and renews itself."
                : token == null ? "" : expired() ? "This token has expired. Copy a new one."
                : token.expiresAt() > 0 ? "Expires in " + remaining(token.expiresAt()) + "." : "Expiry unknown; Minecraft will check it.";
        AccountScreenStyle.text(AccountScreenStyle.fit(note, cardWidth - 32), left + 16, top + 117,
                rejected || expired() ? AccountScreenStyle.ERROR : AccountScreenStyle.MUTED);
        buttonList.get(0).enabled = canAdd();
        super.drawScreen(x, y, ticks);
    }

    @Override protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1 && canAdd()) {
            if (refresh != null) accounts.addRefreshToken(refresh);
            else accounts.addToken(token);
            parent.showAccounts();
        } else if (button.id == 3) paste();
        else if (button.id == 2) mc.displayGuiScreen(parent);
    }

    private void paste() {
        String clipboard = getClipboardString();
        token = MinecraftToken.parse(clipboard);
        refresh = token == null ? MicrosoftRefreshToken.parse(clipboard) : null;
        rejected = token == null && refresh == null;
    }

    private boolean expired() { return token != null && token.isExpired(System.currentTimeMillis()); }

    private boolean canAdd() { return (refresh != null || token != null && !expired()) && accounts.isStorageAvailable() && !accounts.isBusy(); }

    /** Remaining lifetime such as "23h 5m"; "expired" once it has passed. */
    static String remaining(long expiresAt) {
        long minutes = (expiresAt - System.currentTimeMillis()) / 60000;
        if (minutes < 0) return "expired";
        if (minutes < 60) return Math.max(1, minutes) + "m";
        return minutes / 60 + "h " + minutes % 60 + "m";
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) actionPerformed(buttonList.get(0));
        else if (isKeyComboCtrlV(key)) paste();
        else if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_DELETE) { token = null; refresh = null; rejected = false; }
    }
}
