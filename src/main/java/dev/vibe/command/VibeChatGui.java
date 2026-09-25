package dev.vibe.command;

import dev.vibe.Vibe;
import java.io.IOException;
import net.minecraft.client.gui.GuiChat;
import org.lwjgl.input.Keyboard;

/** Replaces vanilla chat only to keep dot commands local and offer Tab completion. */
public final class VibeChatGui extends GuiChat {

    @Override
    public void sendChatMessage(String message, boolean addToChat) {
        if (Vibe.getInstance().getCommandManager().execute(message)) {
            if (addToChat) mc.ingameGUI.getChatGUI().addToSentMessages(message);
            return;
        }
        super.sendChatMessage(message, addToChat);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_TAB && inputField.getText().startsWith(".")) {
            String suggestion = Vibe.getInstance().getCommandManager().firstSuggestion(inputField.getText());
            if (suggestion != null) {
                String current = inputField.getText();
                if (suggestion.toLowerCase().startsWith(current.toLowerCase())) {
                    inputField.setText(current + suggestion.substring(current.length()));
                } else {
                    inputField.setText(suggestion);
                }
                inputField.setCursorPositionEnd();
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (inputField != null && inputField.getText().startsWith(".")) {
            String suggestion = Vibe.getInstance().getCommandManager().firstSuggestion(inputField.getText());
            if (suggestion != null && !suggestion.equalsIgnoreCase(inputField.getText())) {
        fontRendererObj.drawStringWithShadow(dev.vibe.language.LanguageManager.format("Tab  %s", suggestion), 4, height - 25, 0xFF8FA5C4);
            }
        }
    }
}
