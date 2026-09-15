package dev.vibe.ui;

import dev.vibe.Vibe;
import dev.vibe.identity.ClientIdentity;
import dev.vibe.language.LanguageManager;
import dev.vibe.module.impl.LanguageModule;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

/** First-run identity screen, intentionally shown before Vibe exposes the main menu. */
public final class GamertagSetupGui extends GuiScreen {

    private static final String[] LANGUAGES = {"English", "Chinese", "Russian", "Japanese", "Bavarian"};
    private final MainMenuShaderManager shaders;
    private final ClientIdentity identity;
    private GuiTextField gamertag;
    private List<String> description;
    private int left, top, panelWidth, panelHeight, descriptionTop, shaderTop, fieldTop, fieldHeight, languageTop;
    private String error = "";

    public GamertagSetupGui(MainMenuShaderManager shaders, ClientIdentity identity) {
        this.shaders = shaders;
        this.identity = identity;
    }

    @Override
    public void initGui() {
        String value = gamertag == null ? identity.getGamertag() : gamertag.getText();
        Keyboard.enableRepeatEvents(true);
        boolean compact = height < 280;
        panelWidth = Math.min(360, width - 24);
        left = (width - panelWidth) / 2;
        int innerWidth = panelWidth - 32;
        description = fontRendererObj.listFormattedStringToWidth(
                LanguageManager.translate("Choose the local gamertag Vibe will display."), innerWidth);
        descriptionTop = compact ? 32 : 41;
        shaderTop = descriptionTop + description.size() * 10 + (compact ? 3 : 6);
        fieldTop = shaderTop + (compact ? 14 : 20);
        fieldHeight = compact ? 25 : 29;
        int actionsTop = fieldTop + fieldHeight + (compact ? 18 : 22);
        int buttonHeight = compact ? 24 : 28;
        languageTop = actionsTop + buttonHeight + (compact ? 10 : 16);
        int flagsTop = languageTop + 14;
        panelHeight = flagsTop + 28 + (compact ? 12 : 16);
        top = (height - panelHeight) / 2;
        buttonList.clear();
        gamertag = new GuiTextField(0, fontRendererObj, left + 27, top + fieldTop + (fieldHeight - 8) / 2, innerWidth - 22, 14);
        gamertag.setEnableBackgroundDrawing(false);
        gamertag.setTextColor(AccountScreenStyle.TEXT);
        gamertag.setMaxStringLength(16);
        gamertag.setFocused(true);
        gamertag.setText(value);
        int buttonWidth = (innerWidth - 8) / 2;
        buttonList.add(new AccountScreenStyle.Button(1, left + 16, top + actionsTop, buttonWidth, buttonHeight, "Continue", "", true, false));
        buttonList.add(new AccountScreenStyle.Button(2, left + 24 + buttonWidth, top + actionsTop, innerWidth - buttonWidth - 8, buttonHeight, "Shader settings", "", false, false));
        int flagWidth = (innerWidth - 4 * 6) / LANGUAGES.length;
        for (int index = 0; index < LANGUAGES.length; index++) {
            buttonList.add(new LanguageFlagButton(10 + index, left + 16 + index * (flagWidth + 6), top + flagsTop, flagWidth, 28));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 1) {
            saveAndContinue();
        } else if (button.id == 2) {
            mc.displayGuiScreen(new ShaderMenuGui(this, shaders));
        } else if (button.id >= 10 && button.id < 15) {
            selectLanguage(LANGUAGES[button.id - 10]);
            initGui();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveAndContinue();
            return;
        }
        gamertag.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        gamertag.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        shaders.draw(width, height);
        GuiRenderState.prepare(false);
        drawRect(0, 0, width, height, 0x85000000);
        AccountScreenStyle.window(left, top, panelWidth, panelHeight);
        AccountScreenStyle.title(AccountScreenStyle.fit("WELCOME TO VIBE", (panelWidth - 32) * 2 / 3), left + 16, top + (height < 280 ? 12 : 17));
        for (int index = 0; index < description.size(); index++) {
            AccountScreenStyle.rawText(description.get(index), left + 16, top + descriptionTop + index * 10, AccountScreenStyle.MUTED);
        }
        AccountScreenStyle.rawText(AccountScreenStyle.fitRaw(LanguageManager.translate("Active shader") + ": " + shaders.getSelected(), panelWidth - 32),
                left + 16, top + shaderTop, AccountScreenStyle.ACCENT);
        AccountScreenStyle.panel(left + 16, top + fieldTop, panelWidth - 32, fieldHeight, AccountScreenStyle.SURFACE,
                gamertag.isFocused() ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
        gamertag.drawTextBox();
        AccountScreenStyle.text("Language", left + 16, top + languageTop, AccountScreenStyle.MUTED);
        if (!error.isEmpty()) {
            AccountScreenStyle.text(AccountScreenStyle.fit(error, panelWidth - 32), left + 16, top + fieldTop + fieldHeight + 5, AccountScreenStyle.ERROR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void updateScreen() {
        gamertag.updateCursorCounter();
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private void saveAndContinue() {
        if (!identity.setGamertag(gamertag.getText())) {
            error = "Use 3-16 letters, numbers, or underscores.";
            return;
        }
        persistLanguage();
        Vibe.getInstance().updateWindowTitle();
        mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
    }

    private void selectLanguage(String language) {
        identity.setLanguage(language);
        LanguageModule module = Vibe.getInstance().getModuleManager().getModule(LanguageModule.class);
        if (module != null) module.getLanguage().setValue(language);
    }

    private void persistLanguage() {
        LanguageModule module = Vibe.getInstance().getModuleManager().getModule(LanguageModule.class);
        if (module != null) {
            identity.setLanguage(module.getLanguage().getValue());
            Vibe.getInstance().getConfig().save(Vibe.getInstance().getModuleManager());
        }
    }

    private static final class LanguageFlagButton extends GuiButton {
        private LanguageFlagButton(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height, "");
        }

        @Override public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
            String language = LANGUAGES[id - 10];
            boolean selected = language.equalsIgnoreCase(LanguageManager.selectedLanguage());
            AccountScreenStyle.panel(xPosition, yPosition, width, height,
                    hovered ? AccountScreenStyle.HOVER : selected ? AccountScreenStyle.TINT : AccountScreenStyle.SURFACE,
                    selected || hovered ? AccountScreenStyle.ACCENT : AccountScreenStyle.BORDER);
            LanguageFlags.draw(language, xPosition + (width - 24) / 2, yPosition + (height - 16) / 2);
        }
    }
}
