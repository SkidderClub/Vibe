package dev.vibe.command;

import dev.vibe.Vibe;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C14PacketTabComplete;

/** Replaces vanilla chat only to keep dot commands local and offer Tab completion. */
public final class VibeChatGui extends GuiChat {
    private String[] localResponse;

    @Override
    public void sendChatMessage(String message, boolean addToChat) {
        if (Vibe.getInstance().getCommandManager().execute(message)) {
            if (addToChat) mc.ingameGUI.getChatGUI().addToSentMessages(message);
            return;
        }
        super.sendChatMessage(message, addToChat);
    }

    @Override
    public void autocompletePlayerNames() {
        super.autocompletePlayerNames();
        // The request sets vanilla's waiting flag after dispatch. Deliver only
        // after its method returns, including the recursive vanilla selection.
        if (localResponse != null) {
            String[] response = localResponse;
            localResponse = null;
            onAutocompleteResponse(response);
        }
    }

    public static void sendAutocomplete(Object handler, Object packet, Object screen) {
        if (screen instanceof VibeChatGui && packet instanceof C14PacketTabComplete) {
            String text = ((C14PacketTabComplete) packet).getMessage();
            if (text.startsWith(".")) {
                ((VibeChatGui) screen).localResponse = Vibe.getInstance().getCommandManager()
                        .getSuggestions(text).toArray(new String[0]);
                return;
            }
        }
        ((NetHandlerPlayClient) handler).addToSendQueue((Packet<?>) packet);
    }

    /** Local candidates are complete command lines; vanilla names remain last-word replacements. */
    public static int completionStart(Object field, int words, int position, boolean skipSpaces, Object screen) {
        GuiTextField text = (GuiTextField) field;
        return screen instanceof VibeChatGui && text.getText().startsWith(".") ? 0
                : text.func_146197_a(words, position, skipSpaces);
    }

    public static String completionPrefix(String text, int start, Object screen) {
        return screen instanceof VibeChatGui && text.startsWith(".")
                ? text.substring(start, ((VibeChatGui) screen).inputField.getCursorPosition()) : text.substring(start);
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
