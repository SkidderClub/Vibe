package dev.vibe.command;

import dev.vibe.Vibe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiChat;
import org.lwjgl.input.Keyboard;

/** Replaces vanilla chat only to keep dot commands local and offer Tab completion. */
public final class VibeChatGui extends GuiChat {

    private static final List<String> COMMAND_HISTORY = new ArrayList<String>();
    private int historyIndex = -1;
    private String historyDraft = "";

    @Override
    public void sendChatMessage(String message, boolean addToChat) {
        if (Vibe.getInstance().getCommandManager().execute(message)) {
            String command = message == null ? "" : message.trim();
            if (!command.isEmpty() && (COMMAND_HISTORY.isEmpty() || !COMMAND_HISTORY.get(COMMAND_HISTORY.size() - 1).equals(command))) {
                COMMAND_HISTORY.add(command);
                if (COMMAND_HISTORY.size() > 100) COMMAND_HISTORY.remove(0);
            }
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
        if (keyCode == Keyboard.KEY_UP && !COMMAND_HISTORY.isEmpty()
                && (historyIndex >= 0 || inputField.getText().startsWith("."))) {
            if (historyIndex < 0) historyDraft = inputField.getText();
            historyIndex = historyIndex < 0 ? COMMAND_HISTORY.size() - 1 : Math.max(0, historyIndex - 1);
            inputField.setText(COMMAND_HISTORY.get(historyIndex));
            inputField.setCursorPositionEnd();
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN && historyIndex >= 0) {
            historyIndex++;
            if (historyIndex >= COMMAND_HISTORY.size()) {
                historyIndex = -1;
                inputField.setText(historyDraft);
            } else {
                inputField.setText(COMMAND_HISTORY.get(historyIndex));
            }
            inputField.setCursorPositionEnd();
            return;
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
