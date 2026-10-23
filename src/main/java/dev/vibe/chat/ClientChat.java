package dev.vibe.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/** One local-only message format for commands, modules and scripts. */
public final class ClientChat {
    // Vanilla formatting codes are zero-width and survive chat wrapping. The
    // marker lets the font hook recognize our prefix without coloring server chat.
    static final String NAME_MARKER = "\u00a7r\u00a7r\u00a7r";
    public static final String PREFIX = "\u00a78[" + NAME_MARKER + "Vibe\u00a7r\u00a78]\u00a7r ";

    private ClientChat() { }

    public static void send(String text) {
        send(new ChatComponentText(text == null ? "" : text));
    }

    public static void send(IChatComponent body) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.thePlayer != null && body != null) {
            mc.thePlayer.addChatMessage(message(body));
        }
    }

    public static IChatComponent message(IChatComponent body) {
        IChatComponent copy = body.createCopy();
        if (copy.getChatStyle().getColor() == null) copy.getChatStyle().setColor(EnumChatFormatting.GRAY);
        return new ChatComponentText(PREFIX).appendSibling(copy);
    }
}
